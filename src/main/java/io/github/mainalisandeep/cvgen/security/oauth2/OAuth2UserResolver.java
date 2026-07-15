package io.github.mainalisandeep.cvgen.security.oauth2;

import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.entity.UserIdentity;
import io.github.mainalisandeep.cvgen.repository.UserIdentityRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Component
public class OAuth2UserResolver {

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final OAuth2UserInfoFactory userInfoFactory;
    private final String emailUnverifiedConflictErrorCode;

    public OAuth2UserResolver(
            UserRepository userRepository,
            UserIdentityRepository userIdentityRepository,
            OAuth2UserInfoFactory userInfoFactory,
            @Value("${app.security.oauth2.email-unverified-conflict-error-code:email_unverified_conflict}") String emailUnverifiedConflictErrorCode
    ) {
        this.userRepository = userRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.userInfoFactory = userInfoFactory;
        this.emailUnverifiedConflictErrorCode = emailUnverifiedConflictErrorCode;
    }

    @Transactional
    public User resolve(String provider, Map<String, Object> attributes) {
        OAuth2UserInfo userInfo = userInfoFactory.getUserInfo(provider, attributes);

        // 1. Check if this identity already exists
        UserIdentity existingIdentity = userIdentityRepository
                .findByProviderAndProviderId(provider, userInfo.getId())
                .orElse(null);

        if (existingIdentity != null) {
            return existingIdentity.getUser();
        }

        // 2. No existing identity — check by email
        User existingUser = userRepository.findByEmail(userInfo.getEmail()).orElse(null);

        if (existingUser != null) {
            if (userInfo.isEmailVerified()) {
                // Real linking: verified email = same person, attach new identity
                UserIdentity newIdentity = UserIdentity.builder()
                        .user(existingUser)
                        .provider(provider)
                        .providerId(userInfo.getId())
                        .emailAtProvider(userInfo.getEmail())
                        .createdAt(Instant.now())
                        .build();
                userIdentityRepository.save(newIdentity);

                if (!existingUser.isEmailVerified()) {
                    existingUser.setEmailVerified(true);
                    userRepository.save(existingUser);
                }
                return existingUser;
            } else {
                // Unverified email on existing account = account takeover risk
                throw new OAuth2AuthenticationException(
                        new OAuth2Error(emailUnverifiedConflictErrorCode),
                        "Email is not verified by the provider and conflicts with an existing account"
                );
            }
        }

        // 3. No user at all — create new user + identity
        return createNewUserWithIdentity(userInfo, provider);
    }

    private User createNewUserWithIdentity(OAuth2UserInfo userInfo, String provider) {
        User newUser = User.builder()
                .email(userInfo.getEmail())
                .name(userInfo.getName())
                .emailVerified(userInfo.isEmailVerified())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        User savedUser = userRepository.save(newUser);

        UserIdentity identity = UserIdentity.builder()
                .user(savedUser)
                .provider(provider)
                .providerId(userInfo.getId())
                .emailAtProvider(userInfo.getEmail())
                .createdAt(Instant.now())
                .build();
        userIdentityRepository.save(identity);

        return savedUser;
    }
}

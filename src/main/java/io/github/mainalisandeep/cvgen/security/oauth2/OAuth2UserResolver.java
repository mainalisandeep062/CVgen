package io.github.mainalisandeep.cvgen.security.oauth2;

import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.entity.UserIdentity;
import io.github.mainalisandeep.cvgen.records.ProfilePictureSeedRequested;
import io.github.mainalisandeep.cvgen.repository.UserIdentityRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
public class OAuth2UserResolver {

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final OAuth2UserInfoFactory userInfoFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final String emailUnverifiedConflictErrorCode;

    public OAuth2UserResolver(
            UserRepository userRepository,
            UserIdentityRepository userIdentityRepository,
            OAuth2UserInfoFactory userInfoFactory,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.security.oauth2.email-unverified-conflict-error-code:email_unverified_conflict}") String emailUnverifiedConflictErrorCode
    ) {
        this.userRepository = userRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.userInfoFactory = userInfoFactory;
        this.eventPublisher = eventPublisher;
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
            refreshAvatarUrl(existingIdentity, userInfo.getImageUrl());
            return existingIdentity.getUser();
        }

        // 2. No existing identity - check by email
        User existingUser = userRepository.findByEmail(userInfo.getEmail()).orElse(null);

        if (existingUser != null) {
            if (userInfo.isEmailVerified()) {
                // Real linking: verified email = same person, attach new identity
                UserIdentity newIdentity = UserIdentity.builder()
                        .user(existingUser)
                        .provider(provider)
                        .providerId(userInfo.getId())
                        .emailAtProvider(userInfo.getEmail())
                        .avatarUrlAtProvider(userInfo.getImageUrl())
                        .build();
                userIdentityRepository.save(newIdentity);

                // A newly linked provider only supplies a picture when the account still has none;
                // it must never overwrite one the user chose.
                if (existingUser.getProfilePictureFile() == null && userInfo.getImageUrl() != null) {
                    eventPublisher.publishEvent(
                            new ProfilePictureSeedRequested(existingUser.getId(), newIdentity.getId()));
                }

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

        // 3. No user at all - create new user + identity
        return createNewUserWithIdentity(userInfo, provider);
    }

    private User createNewUserWithIdentity(OAuth2UserInfo userInfo, String provider) {
        User newUser = User.builder()
                .email(userInfo.getEmail())
                .name(userInfo.getName())
                .emailVerified(userInfo.isEmailVerified())
                .build();
        User savedUser = userRepository.save(newUser);

        UserIdentity identity = UserIdentity.builder()
                .user(savedUser)
                .provider(provider)
                .providerId(userInfo.getId())
                .emailAtProvider(userInfo.getEmail())
                .avatarUrlAtProvider(userInfo.getImageUrl())
                .build();
        userIdentityRepository.save(identity);

        // The identity that created the account is the default picture, whichever provider that
        // happened to be - no provider ranking, and it always exists.
        if (userInfo.getImageUrl() != null) {
            eventPublisher.publishEvent(new ProfilePictureSeedRequested(savedUser.getId(), identity.getId()));
        }

        return savedUser;
    }

    /**
     * Snapshots what the provider reports, every login.
     * <p>
     * Only the identity row moves. {@code users.profile_picture_file_id} is never touched here:
     * that column carries the user's choice, and a re-login must not stomp it. Because the
     * chosen picture is a copy we own, refreshing this URL still keeps the picker current
     * without putting an expiring CDN link on the display path.
     */
    private void refreshAvatarUrl(UserIdentity identity, String avatarUrl) {
        if (avatarUrl == null || avatarUrl.equals(identity.getAvatarUrlAtProvider())) {
            return;
        }
        identity.setAvatarUrlAtProvider(avatarUrl);
        userIdentityRepository.save(identity);
    }
}

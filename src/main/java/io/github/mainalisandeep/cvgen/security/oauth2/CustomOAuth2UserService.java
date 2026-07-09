package io.github.mainalisandeep.cvgen.security.oauth2;

import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.security.UserPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final UserRepository userRepository;
    private final OAuth2UserInfoFactory userInfoFactory;

    public CustomOAuth2UserService(
            UserRepository userRepository,
            OAuth2UserInfoFactory userInfoFactory
    ) {
        this.userRepository = userRepository;
        this.userInfoFactory = userInfoFactory;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = delegate.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserInfo userInfo = userInfoFactory.getUserInfo(registrationId, oauth2User.getAttributes());

        User user = userRepository.findByProviderAndProviderId(registrationId, userInfo.getId())
                .orElseGet(() -> createNewUser(registrationId, userInfo));

        Set<SimpleGrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        String username = firstNonBlank(userInfo.getEmail(), userInfo.getName(), registrationId + ":" + userInfo.getId());
        return UserPrincipal.oauth2User(
                user.getId().toString(),
                registrationId,
                userInfo.getName(),
                username,
                userInfo.getEmail(),
                userInfo.getImageUrl(),
                oauth2User.getAttributes(),
                authorities
        );
    }

    private User createNewUser(String provider, OAuth2UserInfo userInfo) {
        User user = User.builder()
                .email(userInfo.getEmail())
                .name(userInfo.getName())
                .provider(provider)
                .providerId(userInfo.getId())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return userRepository.save(user);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}

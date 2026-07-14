package io.github.mainalisandeep.cvgen.security.oauth2;

import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.security.UserPrincipal;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final OAuth2UserResolver oAuth2UserResolver;
    private final OAuth2UserInfoFactory userInfoFactory;

    public CustomOAuth2UserService(
            OAuth2UserResolver oAuth2UserResolver,
            OAuth2UserInfoFactory userInfoFactory
    ) {
        this.oAuth2UserResolver = oAuth2UserResolver;
        this.userInfoFactory = userInfoFactory;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = delegate.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        Map<String, Object> attributes = new LinkedHashMap<>(oauth2User.getAttributes());

        // GitHub: /user endpoint doesn't expose email verification status
        // Must call /user/emails to get the primary email's verified flag
        if ("github".equalsIgnoreCase(registrationId)) {
            enrichGithubEmailVerification(userRequest, attributes);
        }

        // Resolve or link user via shared resolver
        User user = oAuth2UserResolver.resolve(registrationId, attributes);

        // Build UserPrincipal with the current session's provider
        OAuth2UserInfo userInfo = userInfoFactory.getUserInfo(registrationId, attributes);

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
                attributes,
                authorities
        );
    }

    private void enrichGithubEmailVerification(OAuth2UserRequest userRequest, Map<String, Object> attributes) {
        try {
            String accessToken = userRequest.getAccessToken().getTokenValue();
            RestTemplate restTemplate = new RestTemplate();

            RequestEntity<Void> request = RequestEntity
                    .get(URI.create("https://api.github.com/user/emails"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .build();

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    request,
                    new ParameterizedTypeReference<>() {}
            );

            List<Map<String, Object>> emails = response.getBody();
            if (emails != null) {
                // Find primary email and use its verified status
                for (Map<String, Object> emailEntry : emails) {
                    Object primary = emailEntry.get("primary");
                    if (Boolean.TRUE.equals(primary) || "true".equals(String.valueOf(primary))) {
                        Object verified = emailEntry.get("verified");
                        attributes.put("email_verified", Boolean.TRUE.equals(verified) || "true".equals(String.valueOf(verified)));
                        // Also use the primary email if the /user endpoint didn't have one
                        if (!attributes.containsKey("email") || attributes.get("email") == null) {
                            attributes.put("email", emailEntry.get("email"));
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // If the API call fails, default to not verified
            attributes.put("email_verified", false);
        }
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

package io.github.mainalisandeep.cvgen.security.oauth2;

import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.security.UserPrincipal;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    //String literals should not be duplicated
    //Detected by SonarQube, string literals should not be duplicated to avoid inconsistencies and facilitate maintenance.
    private static final String EMAIL = "email";
    private static final String EMAIL_VERIFIED = "email_verified";

    private static final URI GITHUB_EMAILS_URI = URI.create("https://api.github.com/user/emails");
    private static final Duration GITHUB_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration GITHUB_READ_TIMEOUT = Duration.ofSeconds(5);

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final OAuth2UserResolver oAuth2UserResolver;
    private final OAuth2UserInfoFactory userInfoFactory;

    /**
     * The GitHub call runs inside the login request thread, so the timeouts are
     * not optional: without them a hanging response stalls authentication.
     */
    private final RestTemplate githubRestTemplate;

    public CustomOAuth2UserService(
            OAuth2UserResolver oAuth2UserResolver,
            OAuth2UserInfoFactory userInfoFactory
    ) {
        this.oAuth2UserResolver = oAuth2UserResolver;
        this.userInfoFactory = userInfoFactory;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(GITHUB_CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(GITHUB_READ_TIMEOUT);
        this.githubRestTemplate = new RestTemplate(requestFactory);
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

    /**
     * The verified flag only describes the primary email, so both values have to be
     * written together. Overwriting the /user email with a verified flag that belongs
     * to a different address would let OAuth2UserResolver auto-link the wrong account.
     */
    void enrichGithubEmailVerification(OAuth2UserRequest userRequest, Map<String, Object> attributes) {
        // Fail closed: anything short of a confirmed primary email leaves the account unverified
        attributes.put(EMAIL_VERIFIED, false);
        try {
            String accessToken = userRequest.getAccessToken().getTokenValue();

            RequestEntity<Void> request = RequestEntity
                    .get(GITHUB_EMAILS_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .build();

            ResponseEntity<List<Map<String, Object>>> response = githubRestTemplate.exchange(
                    request,
                    new ParameterizedTypeReference<>() {}
            );

            List<Map<String, Object>> emails = response.getBody();
            if (emails == null) {
                return;
            }

            // Find primary email and use it, together with its verified status
            for (Map<String, Object> emailEntry : emails) {
                if (!Boolean.TRUE.equals(emailEntry.get("primary"))) {
                    continue;
                }
                Object primaryEmail = emailEntry.get(EMAIL);
                if (primaryEmail == null || String.valueOf(primaryEmail).isBlank()) {
                    // Keep whatever /user returned, but never claim it is verified:
                    // the flag we just read belongs to an address we did not get
                    return;
                }
                attributes.put(EMAIL, String.valueOf(primaryEmail));
                attributes.put(EMAIL_VERIFIED, Boolean.TRUE.equals(emailEntry.get("verified")));
                return;
            }
        } catch (Exception e) {
            // If the API call fails, default to not verified
            attributes.put(EMAIL_VERIFIED, false);
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

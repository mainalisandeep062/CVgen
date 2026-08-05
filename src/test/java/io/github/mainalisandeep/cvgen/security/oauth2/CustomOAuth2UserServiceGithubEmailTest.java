package io.github.mainalisandeep.cvgen.security.oauth2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The email and the email_verified flag must always describe the same address:
 * OAuth2UserResolver auto-links accounts on that pair.
 */
class CustomOAuth2UserServiceGithubEmailTest {

    private static final String EMAILS_URI = "https://api.github.com/user/emails";

    private CustomOAuth2UserService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        service = new CustomOAuth2UserService(null, null);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "githubRestTemplate");
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    @DisplayName("Primary email replaces the non-primary email from /user and carries its own verified flag")
    void primaryEmailWinsOverUserEndpointEmail() {
        server.expect(requestTo(EMAILS_URI))
                .andExpect(header("Authorization", "Bearer token-123"))
                .andRespond(withSuccess("""
                        [
                          {"email": "secondary@example.com", "primary": false, "verified": false},
                          {"email": "primary@example.com", "primary": true, "verified": true}
                        ]
                        """, MediaType.APPLICATION_JSON));

        Map<String, Object> attributes = attributesWithEmail("secondary@example.com");
        service.enrichGithubEmailVerification(userRequest(), attributes);

        assertThat(attributes).containsEntry("email", "primary@example.com");
        assertThat(attributes).containsEntry("email_verified", true);
        server.verify();
    }

    @Test
    @DisplayName("A verified non-primary entry never marks the primary email as verified")
    void unverifiedPrimaryIsNotRescuedByVerifiedSecondary() {
        server.expect(requestTo(EMAILS_URI))
                .andRespond(withSuccess("""
                        [
                          {"email": "verified-secondary@example.com", "primary": false, "verified": true},
                          {"email": "primary@example.com", "primary": true, "verified": false}
                        ]
                        """, MediaType.APPLICATION_JSON));

        Map<String, Object> attributes = attributesWithEmail("verified-secondary@example.com");
        service.enrichGithubEmailVerification(userRequest(), attributes);

        assertThat(attributes).containsEntry("email", "primary@example.com");
        assertThat(attributes).containsEntry("email_verified", false);
    }

    @Test
    @DisplayName("Missing primary email keeps the /user email and leaves it unverified")
    void blankPrimaryEmailDoesNotWipeExistingEmail() {
        server.expect(requestTo(EMAILS_URI))
                .andRespond(withSuccess("""
                        [{"email": null, "primary": true, "verified": true}]
                        """, MediaType.APPLICATION_JSON));

        Map<String, Object> attributes = attributesWithEmail("from-user-endpoint@example.com");
        service.enrichGithubEmailVerification(userRequest(), attributes);

        assertThat(attributes).containsEntry("email", "from-user-endpoint@example.com");
        assertThat(attributes).containsEntry("email_verified", false);
    }

    @Test
    @DisplayName("No primary entry at all leaves the account unverified")
    void noPrimaryEntryFailsClosed() {
        server.expect(requestTo(EMAILS_URI))
                .andRespond(withSuccess("""
                        [{"email": "secondary@example.com", "primary": false, "verified": true}]
                        """, MediaType.APPLICATION_JSON));

        Map<String, Object> attributes = attributesWithEmail("secondary@example.com");
        service.enrichGithubEmailVerification(userRequest(), attributes);

        assertThat(attributes).containsEntry("email", "secondary@example.com");
        assertThat(attributes).containsEntry("email_verified", false);
    }

    @Test
    @DisplayName("A failing GitHub call leaves the account unverified")
    void apiFailureFailsClosed() {
        server.expect(requestTo(EMAILS_URI))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        Map<String, Object> attributes = attributesWithEmail("user@example.com");
        service.enrichGithubEmailVerification(userRequest(), attributes);

        assertThat(attributes).containsEntry("email", "user@example.com");
        assertThat(attributes).containsEntry("email_verified", false);
    }

    private Map<String, Object> attributesWithEmail(String email) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("id", 12345);
        attributes.put("login", "octocat");
        attributes.put("email", email);
        return attributes;
    }

    private OAuth2UserRequest userRequest() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("github")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/github")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .userNameAttributeName("id")
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "token-123",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        return new OAuth2UserRequest(registration, accessToken);
    }
}

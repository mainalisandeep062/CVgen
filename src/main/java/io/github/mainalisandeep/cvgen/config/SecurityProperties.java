package io.github.mainalisandeep.cvgen.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@Validated
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    @Valid
    private final Jwt jwt = new Jwt();

    @Valid
    private final OAuth2 oauth2 = new OAuth2();

    @Valid
    private final Cors cors = new Cors();

    @NotEmpty
    private List<String> publicPaths = new ArrayList<>(List.of(
            "/",
            "/error",
            "/favicon.ico",
            "/assets/**",
            "/css/**",
            "/js/**",
            "/images/**",
            "/webjars/**",
            "/oauth2/**",
            "/login/oauth2/**",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    ));

    public Jwt getJwt() {
        return jwt;
    }

    public OAuth2 getOauth2() {
        return oauth2;
    }

    public Cors getCors() {
        return cors;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public static class Jwt {

        @NotBlank
        private String secret;

        @NotBlank
        private String issuer = "cvgen";

        private Duration expiration = Duration.ofHours(2);

        private Duration clockSkew = Duration.ofMinutes(2);

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public Duration getExpiration() {
            return expiration;
        }

        public void setExpiration(Duration expiration) {
            this.expiration = expiration;
        }

        public Duration getClockSkew() {
            return clockSkew;
        }

        public void setClockSkew(Duration clockSkew) {
            this.clockSkew = clockSkew;
        }
    }

    public static class OAuth2 {

        @NotBlank
        private String redirectUri = "http://localhost:3000/oauth2/redirect";

        @NotBlank
        private String successRedirectUri = "http://localhost:3000/auth/success";

        @NotBlank
        private String failureRedirectUri = "http://localhost:3000/auth/failure";

        @NotEmpty
        private List<String> authorizedRedirectUris = new ArrayList<>(List.of(
                "http://localhost:3000/oauth2/redirect",
                "http://localhost:3000/auth/success"
        ));

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }

        public String getSuccessRedirectUri() {
            return successRedirectUri;
        }

        public void setSuccessRedirectUri(String successRedirectUri) {
            this.successRedirectUri = successRedirectUri;
        }

        public String getFailureRedirectUri() {
            return failureRedirectUri;
        }

        public void setFailureRedirectUri(String failureRedirectUri) {
            this.failureRedirectUri = failureRedirectUri;
        }

        public List<String> getAuthorizedRedirectUris() {
            return authorizedRedirectUris;
        }

        public void setAuthorizedRedirectUris(List<String> authorizedRedirectUris) {
            this.authorizedRedirectUris = authorizedRedirectUris;
        }
    }

    public static class Cors {

        @NotEmpty
        private List<String> allowedOrigins = new ArrayList<>(List.of(
                "http://localhost:3000",
                "http://127.0.0.1:3000"
        ));

        @NotEmpty
        private List<String> allowedMethods = new ArrayList<>(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        @NotEmpty
        private List<String> allowedHeaders = new ArrayList<>(List.of(
                "Authorization",
                "Cache-Control",
                "Content-Type",
                "X-Requested-With"
        ));

        @NotEmpty
        private List<String> exposedHeaders = new ArrayList<>(List.of(
                "Authorization"
        ));

        private boolean allowCredentials = true;

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public List<String> getAllowedHeaders() {
            return allowedHeaders;
        }

        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        public List<String> getExposedHeaders() {
            return exposedHeaders;
        }

        public void setExposedHeaders(List<String> exposedHeaders) {
            this.exposedHeaders = exposedHeaders;
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }
    }
}

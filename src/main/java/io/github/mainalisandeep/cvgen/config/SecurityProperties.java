package io.github.mainalisandeep.cvgen.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
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

    @Valid
    private final Messages messages = new Messages();

    @Valid
    private final Async async = new Async();

    @Valid
    private final Web web = new Web();

    @NotEmpty
    private List<String> publicPaths;

    public Jwt getJwt() {
        return jwt;
    }

    public OAuth2 getOauth2() {
        return oauth2;
    }

    public Cors getCors() {
        return cors;
    }

    public Messages getMessages() {
        return messages;
    }

    public Async getAsync() {
        return async;
    }

    public Web getWeb() {
        return web;
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
        private String issuer;

        private Duration expiration;

        private Duration clockSkew;

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
        private String redirectUri;

        @NotBlank
        private String successRedirectUri;

        @NotBlank
        private String failureRedirectUri;

        @NotEmpty
        private List<String> authorizedRedirectUris;

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
        private List<String> allowedOrigins;

        @NotEmpty
        private List<String> allowedMethods;

        @NotEmpty
        private List<String> allowedHeaders;

        @NotEmpty
        private List<String> exposedHeaders;

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

    public static class Messages {

        @NotBlank
        private String unauthorized;

        @NotBlank
        private String forbidden;

        public String getUnauthorized() {
            return unauthorized;
        }

        public void setUnauthorized(String unauthorized) {
            this.unauthorized = unauthorized;
        }

        public String getForbidden() {
            return forbidden;
        }

        public void setForbidden(String forbidden) {
            this.forbidden = forbidden;
        }
    }

    public static class Async {

        private int corePoolSize;

        private int maxPoolSize;

        private int queueCapacity;

        @NotBlank
        private String threadNamePrefix;

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public String getThreadNamePrefix() {
            return threadNamePrefix;
        }

        public void setThreadNamePrefix(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
        }
    }

    public static class Web {

        private int assetsCachePeriod;

        public int getAssetsCachePeriod() {
            return assetsCachePeriod;
        }

        public void setAssetsCachePeriod(int assetsCachePeriod) {
            this.assetsCachePeriod = assetsCachePeriod;
        }
    }
}


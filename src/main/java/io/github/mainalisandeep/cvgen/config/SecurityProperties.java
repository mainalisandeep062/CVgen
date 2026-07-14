package io.github.mainalisandeep.cvgen.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    @Valid
    private Jwt jwt = new Jwt();

    @Valid
    private OAuth2 oauth2 = new OAuth2();

    @Valid
    private Cors cors = new Cors();

    @Valid
    private Messages messages = new Messages();

    @Valid
    private Async async = new Async();

    @Valid
    private Web web = new Web();

    @Data
    public static class Jwt {

        @NotBlank
        private String secret;

        @NotBlank
        private String issuer;

        private Duration expiration;

        private Duration clockSkew;
    }


    @Data
    public static class OAuth2 {

        @NotBlank
        private String redirectUri;

        @NotBlank
        private String successRedirectUri;

        @NotBlank
        private String failureRedirectUri;

        @NotEmpty
        private List<String> authorizedRedirectUris;

        private String googleProviderId;

        private String githubProviderId;

        private String linkedinProviderId;

        private String unsupportedProviderErrorCode;

        private String failureErrorCode;

        private String emailUnverifiedConflictErrorCode;
    }


    @Data
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
    }


    @Data
    public static class Messages {

        @NotBlank
        private String unauthorized;

        @NotBlank
        private String forbidden;
    }


    @Data
    public static class Async {

        private int corePoolSize = 5;

        private int maxPoolSize = 10;

        private int queueCapacity = 100;

        @NotBlank
        private String threadNamePrefix = "cvgen-async-";
    }


    @Data
    public static class Web {

        private int assetsCachePeriod = 3600;
    }
}

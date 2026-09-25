package io.github.mainalisandeep.cvgen.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * GitHub's public REST API, used only to list a user's public repositories for the project import.
 * Nothing here is an OAuth credential; sign-in with GitHub is configured under {@code spring.security}.
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "app.github")
public class GithubProperties {

    @NotBlank
    private String apiBaseUrl = "https://api.github.com";

    /** Optional, scope-less token that only raises the rate limit. Blank means unauthenticated. */
    private String token = "";

    /** Connect and read timeout each. A slow GitHub must not hold a web worker. */
    @NotNull
    private Duration timeout = Duration.ofSeconds(5);
}

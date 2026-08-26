package io.github.mainalisandeep.cvgen.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Where uploaded and fetched blobs live, and the limits applied before anything is stored.
 * <p>
 * Every value has a working default so the application and the test context boot without
 * extra configuration; only {@code public-base-url} normally needs setting per environment.
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /**
     * Absolute or relative directory the local backend writes into.
     * Replaced wholesale by the bucket when this moves to R2.
     */
    @NotBlank
    private String localRoot = "storage";

    /**
     * Origin the frontend should prefix file URLs with, e.g. {@code https://api.cvgen.io}.
     * Blank yields same-origin relative URLs, which only work when the API and the page
     * share an origin - set it whenever the frontend is served separately.
     */
    private String publicBaseUrl = "";

    @Valid
    private Upload upload = new Upload();

    @Valid
    private RemoteFetch remoteFetch = new RemoteFetch();

    @Data
    public static class Upload {

        /** Hard ceiling on a stored profile picture, enforced after the bytes are read. */
        @Positive
        private long maxProfilePictureBytes = 2L * 1024 * 1024;

        /** Allow-list, not a block-list: anything unlisted is rejected rather than sniffed. */
        private List<String> allowedImageMimeTypes = List.of(
                "image/jpeg", "image/png", "image/webp", "image/gif"
        );
    }

    /**
     * Limits on fetching a provider avatar. This is the one place the server follows a URL
     * it did not construct, so the constraints here are load-bearing, not tuning knobs.
     */
    @Data
    public static class RemoteFetch {

        private Duration connectTimeout = Duration.ofSeconds(3);

        private Duration readTimeout = Duration.ofSeconds(5);

        /** Redirect chains are followed manually so each hop can be re-validated. */
        @Positive
        private int maxRedirects = 3;
    }
}

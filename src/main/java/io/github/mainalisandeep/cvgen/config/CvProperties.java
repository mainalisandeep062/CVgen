package io.github.mainalisandeep.cvgen.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Limits and defaults applied to a CV before it is stored.
 * <p>
 * Every value has a working default so the application and the test context boot without extra
 * configuration. The two caps are not tuning knobs: an unbounded document column and an
 * unbounded row count per user are both denial-of-service surfaces, and both are far cheaper to
 * set now than once someone has five hundred CVs.
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "app.cv")
public class CvProperties {

    /** Hard ceiling on how many CVs one user may own. */
    @Positive
    private int maxPerUser = 20;

    /**
     * Ceiling on the serialised content document, mirrored by {@code chk_cvs_content_size}.
     * The service rejects an oversized document with a readable error; the constraint is the
     * backstop for anything reaching the table another way.
     */
    @Positive
    private long maxContentBytes = 256L * 1024;

    /** Template a CV renders with until the caller picks another. */
    @NotBlank
    private String defaultTemplateKey = "classic";

    /** Language a new CV is written in, independent of the caller's UI locale. */
    @NotBlank
    private String defaultLocale = "en";
}

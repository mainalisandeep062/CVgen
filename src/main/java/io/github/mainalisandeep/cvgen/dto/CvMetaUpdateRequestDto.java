package io.github.mainalisandeep.cvgen.dto;

import io.github.mainalisandeep.cvgen.enums.CvStatus;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * List-screen metadata only, never the document. Every field is optional; null means
 * "leave as is", so renaming a CV does not require resending its template and locale.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CvMetaUpdateRequestDto {

    @Size(min = 1, max = 255, message = "{validation.cv.title.size}")
    private String title;

    @Size(max = 64, message = "{validation.cv.template.size}")
    private String templateKey;

    @Size(max = 16, message = "{validation.cv.locale.size}")
    private String locale;

    private CvStatus status;
}

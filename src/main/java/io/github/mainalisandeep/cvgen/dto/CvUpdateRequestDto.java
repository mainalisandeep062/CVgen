package io.github.mainalisandeep.cvgen.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Full replacement of the content document. Partial section updates get their own endpoint
 * in the editing phase; this one always carries the whole tree.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CvUpdateRequestDto {

    @NotNull(message = "{validation.cv.content.required}")
    private JsonNode content;
}

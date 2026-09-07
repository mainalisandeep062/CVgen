package io.github.mainalisandeep.cvgen.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CvCreateRequestDto {

    @NotBlank(message = "{validation.cv.title.required}")
    @Size(max = 255, message = "{validation.cv.title.size}")
    private String title;

    @Size(max = 64, message = "{validation.cv.template.size}")
    private String templateKey;

    @Size(max = 16, message = "{validation.cv.locale.size}")
    private String locale;

    /**
     * Optional. Omitted means "start me an empty CV" - the server seeds the v1 starter
     * document rather than making the client know its shape.
     */
    private JsonNode content;
}

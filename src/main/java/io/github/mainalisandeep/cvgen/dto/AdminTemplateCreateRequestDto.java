package io.github.mainalisandeep.cvgen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A new template: the editable fields plus the key, which cannot change afterwards.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class AdminTemplateCreateRequestDto extends AdminTemplateUpdateRequestDto {

    /** Lowercase so a key never differs from another only by case in a URL or a stored CV. */
    @NotBlank(message = "{validation.template.key.required}")
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,62}$", message = "{validation.template.key.pattern}")
    private String key;
}

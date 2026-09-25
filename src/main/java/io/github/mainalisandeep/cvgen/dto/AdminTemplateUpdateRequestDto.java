package io.github.mainalisandeep.cvgen.dto;

import io.github.mainalisandeep.cvgen.enums.CvSectionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * Full replacement of a template's editable fields. Optional fields fall back to the same defaults
 * as on create, so omitting one resets it rather than keeping the stored value.
 * <p>
 * There is no {@code key} here: it is immutable, and a {@code key} sent anyway is ignored as an
 * unknown property.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class AdminTemplateUpdateRequestDto {

    @NotBlank(message = "{validation.template.name.required}")
    @Size(max = 120, message = "{validation.template.name.size}")
    private String name;

    @Size(max = 500, message = "{validation.template.description.size}")
    private String description;

    @NotBlank(message = "{validation.template.layout.required}")
    @Size(max = 64, message = "{validation.template.layout.size}")
    private String layout;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "{validation.template.accent.color.pattern}")
    private String accentColor;

    /** Null means every section the layout supports. */
    private List<CvSectionType> supportedSections;

    private Boolean premium;

    /** Forced to 0 unless {@link #premium}. */
    @Min(value = 0, message = "{validation.template.credit.cost.range}")
    @Max(value = 1000, message = "{validation.template.credit.cost.range}")
    private Integer creditCost;

    private Boolean active;

    private Integer sortOrder;
}

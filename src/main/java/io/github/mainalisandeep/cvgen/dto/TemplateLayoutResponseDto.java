package io.github.mainalisandeep.cvgen.dto;

import io.github.mainalisandeep.cvgen.enums.CvSectionType;

import java.util.List;

/**
 * A renderer an admin can base a template on.
 *
 * @param supportedSections the most a template of this layout may show
 */
public record TemplateLayoutResponseDto(
        String key,
        String name,
        List<CvSectionType> supportedSections
) {
}

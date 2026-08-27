package io.github.mainalisandeep.cvgen.dto;

import io.github.mainalisandeep.cvgen.enums.CvSectionType;

import java.util.List;

/**
 * One entry of the template picker.
 *
 * @param key               value to send as {@code templateKey} on create or metadata update
 * @param name              display name
 * @param supportedSections section types the template draws; others are kept but not rendered
 */
public record CvTemplateResponseDto(
        String key,
        String name,
        String description,
        List<CvSectionType> supportedSections
) {
}

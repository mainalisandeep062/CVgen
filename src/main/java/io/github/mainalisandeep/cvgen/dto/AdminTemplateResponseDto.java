package io.github.mainalisandeep.cvgen.dto;

import io.github.mainalisandeep.cvgen.enums.CvSectionType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A template as admins manage it, including inactive ones.
 *
 * @param cvCount CVs currently rendering with this template; a non-zero count blocks deletion
 */
public record AdminTemplateResponseDto(
        UUID id,
        String key,
        String name,
        String description,
        String layout,
        String accentColor,
        List<CvSectionType> supportedSections,
        boolean premium,
        int creditCost,
        boolean active,
        int sortOrder,
        long cvCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

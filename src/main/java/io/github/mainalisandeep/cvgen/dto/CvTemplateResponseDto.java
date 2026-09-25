package io.github.mainalisandeep.cvgen.dto;

import io.github.mainalisandeep.cvgen.enums.CvSectionType;

import java.util.List;

/**
 * One entry of the template picker. Active templates only.
 *
 * @param key               value to send as {@code templateKey} on create or metadata update
 * @param name              display name
 * @param supportedSections section types the template draws; others are kept but not rendered
 * @param layout            renderer the template is a variant of
 * @param accentColor       {@code #RRGGBB}, or {@code null} for the layout's own colour
 * @param creditCost        credits a premium template costs, always 0 when not premium
 * @param unlocked          whether the caller may use it: always for a free template, after paying for a premium one
 */
public record CvTemplateResponseDto(
        String key,
        String name,
        String description,
        List<CvSectionType> supportedSections,
        String layout,
        String accentColor,
        boolean premium,
        int creditCost,
        boolean unlocked
) {
}

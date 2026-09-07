package io.github.mainalisandeep.cvgen.dto;

import io.github.mainalisandeep.cvgen.enums.CvStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row of the CV list.
 * <p>
 * Every field here is a real column. Nothing is read out of the content document, which is the
 * rule that keeps the single-table design honest: if the list ever needs the candidate's name,
 * it gets a denormalised column rather than a reach into {@code content}.
 *
 * @param title       user-facing name of the CV, not the candidate's name
 * @param templateKey template the CV renders with
 * @param locale      language the CV itself is written in
 */
public record CvSummaryResponseDto(
        UUID id,
        String title,
        String templateKey,
        String locale,
        CvStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

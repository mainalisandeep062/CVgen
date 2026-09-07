package io.github.mainalisandeep.cvgen.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mainalisandeep.cvgen.enums.CvStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single CV, metadata plus its content document.
 *
 * @param content round-trips unchanged - what was saved comes back, unknown keys included
 * @param version optimistic-lock counter, exposed so the editor can send it back once
 *                partial updates land
 */
public record CvDetailResponseDto(
        UUID id,
        String title,
        String templateKey,
        String locale,
        CvStatus status,
        JsonNode content,
        long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

package io.github.mainalisandeep.cvgen.dto;

import io.github.mainalisandeep.cvgen.enums.NotificationLevel;

import java.time.LocalDateTime;
import java.util.UUID;

/** A notification in the caller's inbox. */
public record NotificationResponseDto(
        UUID id,
        String title,
        String body,
        NotificationLevel level,
        String link,
        boolean read,
        LocalDateTime createdAt
) {
}

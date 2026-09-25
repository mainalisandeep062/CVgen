package io.github.mainalisandeep.cvgen.dto;

import io.github.mainalisandeep.cvgen.enums.NotificationAudience;
import io.github.mainalisandeep.cvgen.enums.NotificationLevel;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A sent notification as admins see it.
 *
 * @param recipientId    {@code null} for a broadcast
 * @param readCount      users who have marked it read
 */
public record AdminNotificationResponseDto(
        UUID id,
        String title,
        String body,
        NotificationLevel level,
        String link,
        NotificationAudience audience,
        UUID recipientId,
        String recipientEmail,
        long readCount,
        String createdByEmail,
        LocalDateTime createdAt
) {
}

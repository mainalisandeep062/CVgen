package io.github.mainalisandeep.cvgen.mapper;

import io.github.mainalisandeep.cvgen.dto.AdminNotificationResponseDto;
import io.github.mainalisandeep.cvgen.dto.NotificationResponseDto;
import io.github.mainalisandeep.cvgen.entity.Notification;
import io.github.mainalisandeep.cvgen.entity.User;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Single place where a {@link Notification} is translated for the layers above it.
 */
@Component
public class NotificationMapper {

    /** Reads the recipient association; fetch it with the query. */
    public AdminNotificationResponseDto toAdminDto(Notification notification, long readCount) {
        User recipient = notification.getRecipient();
        return new AdminNotificationResponseDto(
                notification.getId(),
                notification.getTitle(),
                notification.getBody(),
                notification.getLevel(),
                notification.getLink(),
                notification.getAudience(),
                recipient == null ? null : recipient.getId(),
                recipient == null ? null : recipient.getEmail(),
                readCount,
                notification.getCreatedByEmail(),
                toLocalDateTime(notification.getCreatedAt())
        );
    }

    /** Inbox entry. Deliberately without recipient or sender: the reader knows it is theirs. */
    public NotificationResponseDto toUserDto(Notification notification, boolean read) {
        return new NotificationResponseDto(
                notification.getId(),
                notification.getTitle(),
                notification.getBody(),
                notification.getLevel(),
                notification.getLink(),
                read,
                toLocalDateTime(notification.getCreatedAt())
        );
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}

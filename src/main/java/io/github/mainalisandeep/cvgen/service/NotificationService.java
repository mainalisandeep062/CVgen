package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.dto.NotificationListResponseDto;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * The caller's inbox. A notification is visible when it is addressed to the caller, or when it is a
 * broadcast created at or after the caller's account. Anything else is reported as missing.
 */
public interface NotificationService {

    /** Newest first, with the unread count across all pages. */
    NotificationListResponseDto list(UUID userId, Pageable pageable);

    /**
     * Idempotent.
     *
     * @throws io.github.mainalisandeep.cvgen.common.exception.ResourceNotFoundException when not visible to the caller
     */
    void markRead(UUID userId, UUID notificationId);

    void markAllRead(UUID userId);
}

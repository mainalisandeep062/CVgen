package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.dto.AdminNotificationResponseDto;
import io.github.mainalisandeep.cvgen.dto.NotificationCreateRequestDto;
import io.github.mainalisandeep.cvgen.dto.PageResponseDto;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/** Sending and retracting notifications. */
public interface AdminNotificationService {

    /** Newest first, with how many users have read each one. */
    PageResponseDto<AdminNotificationResponseDto> list(Pageable pageable);

    /**
     * @throws io.github.mainalisandeep.cvgen.common.exception.ResourceNotFoundException when the audience is
     *                                                                                 USER and no account has that email
     */
    AdminNotificationResponseDto send(UUID actorId, NotificationCreateRequestDto request);

    /** Removes it from every inbox, read receipts included. */
    void delete(UUID actorId, UUID notificationId);
}

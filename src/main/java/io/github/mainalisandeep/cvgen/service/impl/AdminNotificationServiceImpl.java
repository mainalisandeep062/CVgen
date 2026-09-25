package io.github.mainalisandeep.cvgen.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mainalisandeep.cvgen.common.exception.ResourceNotFoundException;
import io.github.mainalisandeep.cvgen.common.message.ActivityMessageConstant;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.dto.AdminNotificationResponseDto;
import io.github.mainalisandeep.cvgen.dto.NotificationCreateRequestDto;
import io.github.mainalisandeep.cvgen.dto.PageResponseDto;
import io.github.mainalisandeep.cvgen.entity.Notification;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.enums.AdminAction;
import io.github.mainalisandeep.cvgen.enums.AdminTargetType;
import io.github.mainalisandeep.cvgen.enums.NotificationAudience;
import io.github.mainalisandeep.cvgen.mapper.NotificationMapper;
import io.github.mainalisandeep.cvgen.repository.NotificationReadRepository;
import io.github.mainalisandeep.cvgen.repository.NotificationRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.service.AdminAuditLogService;
import io.github.mainalisandeep.cvgen.service.AdminNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminNotificationServiceImpl implements AdminNotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationReadRepository notificationReadRepository;
    private final UserRepository userRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final NotificationMapper notificationMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<AdminNotificationResponseDto> list(Pageable pageable) {
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Notification> page = notificationRepository.findAll(newestFirst);

        List<UUID> ids = page.getContent().stream().map(Notification::getId).toList();
        Map<UUID, Long> readCounts = ids.isEmpty()
                ? Map.of()
                : notificationReadRepository.countByNotificationIds(ids).stream()
                .collect(Collectors.toMap(NotificationReadRepository.ReadCount::getNotificationId,
                        NotificationReadRepository.ReadCount::getCount));

        return PageResponseDto.of(page, page.getContent().stream()
                .map(notification -> notificationMapper.toAdminDto(notification, readCounts.getOrDefault(notification.getId(), 0L)))
                .toList());
    }

    @Override
    @Transactional
    public AdminNotificationResponseDto send(UUID actorId, NotificationCreateRequestDto request) {
        // A recipient email on a broadcast is ignored rather than rejected: the form may keep the field filled.
        User recipient = request.getAudience() == NotificationAudience.USER
                ? userRepository.findByEmail(request.getRecipientEmail().trim())
                .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.USER))
                : null;

        Notification notification = notificationRepository.save(Notification.builder()
                .title(request.getTitle().trim())
                .body(request.getBody().trim())
                .level(request.getLevel())
                .link(request.getLink() == null || request.getLink().isBlank() ? null : request.getLink().trim())
                .audience(request.getAudience())
                .recipient(recipient)
                .createdById(actorId)
                .createdByEmail(userRepository.findEmailById(actorId).orElse(null))
                .build());

        adminAuditLogService.record(actorId, AdminAction.NOTIFICATION_SENT, AdminTargetType.NOTIFICATION, notification.getId(),
                objectMapper.createObjectNode()
                        .put("title", notification.getTitle())
                        .put("level", notification.getLevel().name())
                        .put("audience", notification.getAudience().name())
                        .put("recipientEmail", recipient == null ? null : recipient.getEmail()),
                recipient == null
                        ? ActivityMessageConstant.AUDIT_NOTIFICATION_BROADCAST
                        : ActivityMessageConstant.AUDIT_NOTIFICATION_SENT,
                notification.getTitle(), recipient == null ? null : recipient.getEmail());

        return notificationMapper.toAdminDto(notification, 0L);
    }

    @Override
    @Transactional
    public void delete(UUID actorId, UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.NOTIFICATION));

        adminAuditLogService.record(actorId, AdminAction.NOTIFICATION_DELETED, AdminTargetType.NOTIFICATION, notification.getId(),
                objectMapper.createObjectNode()
                        .put("title", notification.getTitle())
                        .put("audience", notification.getAudience().name()),
                ActivityMessageConstant.AUDIT_NOTIFICATION_DELETED, notification.getTitle());
        notificationRepository.delete(notification);
    }
}

package io.github.mainalisandeep.cvgen.service.impl;

import io.github.mainalisandeep.cvgen.common.exception.ResourceNotFoundException;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.dto.NotificationListResponseDto;
import io.github.mainalisandeep.cvgen.entity.Notification;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.enums.NotificationAudience;
import io.github.mainalisandeep.cvgen.mapper.NotificationMapper;
import io.github.mainalisandeep.cvgen.repository.NotificationReadRepository;
import io.github.mainalisandeep.cvgen.repository.NotificationRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationReadRepository notificationReadRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;

    @Override
    @Transactional(readOnly = true)
    public NotificationListResponseDto list(UUID userId, Pageable pageable) {
        User user = findUser(userId);
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<Notification> page = notificationRepository.findVisible(userId, NotificationAudience.ALL, user.getCreatedAt(), newestFirst);
        List<UUID> ids = page.getContent().stream().map(Notification::getId).toList();
        Set<UUID> readIds = ids.isEmpty()
                ? Set.of()
                : new HashSet<>(notificationReadRepository.findReadNotificationIds(userId, ids));

        return new NotificationListResponseDto(
                page.getContent().stream()
                        .map(notification -> notificationMapper.toUserDto(notification, readIds.contains(notification.getId())))
                        .toList(),
                notificationRepository.countUnread(userId, NotificationAudience.ALL, user.getCreatedAt()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    @Override
    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        User user = findUser(userId);
        // 404 rather than 403 for someone else's notification, same as a foreign CV: a 403 would confirm
        // the id exists.
        if (!notificationRepository.isVisible(notificationId, userId, NotificationAudience.ALL, user.getCreatedAt())) {
            throw ResourceNotFoundException.of(FieldConstantValue.NOTIFICATION);
        }
        notificationReadRepository.markRead(notificationId, userId);
    }

    @Override
    @Transactional
    public void markAllRead(UUID userId) {
        notificationReadRepository.markAllRead(userId, findUser(userId).getCreatedAt());
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.USER));
    }
}

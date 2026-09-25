package io.github.mainalisandeep.cvgen.dto;

import java.util.List;

/**
 * The caller's inbox: a page plus the unread count across every page, which the badge needs and a
 * page alone cannot give.
 *
 * @param page zero-based index of the returned page
 */
public record NotificationListResponseDto(
        List<NotificationResponseDto> items,
        long unreadCount,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}

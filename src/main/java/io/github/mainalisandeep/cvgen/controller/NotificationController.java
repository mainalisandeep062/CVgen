package io.github.mainalisandeep.cvgen.controller;

import io.github.mainalisandeep.cvgen.common.controller.BaseController;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.common.message.SuccessResponseConstant;
import io.github.mainalisandeep.cvgen.common.response.GlobalApiResponse;
import io.github.mainalisandeep.cvgen.dto.NotificationListResponseDto;
import io.github.mainalisandeep.cvgen.security.util.JwtTokenUtil;
import io.github.mainalisandeep.cvgen.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The caller's own inbox. Like {@code CvController}, it passes the caller id down and makes no
 * authorization decision: a notification that is not visible to the caller is reported as missing.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController extends BaseController {

    private final NotificationService notificationService;
    private final JwtTokenUtil jwtTokenUtil;

    @GetMapping
    public ResponseEntity<GlobalApiResponse<NotificationListResponseDto>> listNotifications(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ok(SuccessResponseConstant.FETCH_SUCCESS,
                notificationService.list(jwtTokenUtil.getCurrentUserId(), pageable), FieldConstantValue.NOTIFICATIONS);
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<GlobalApiResponse<Void>> markRead(@PathVariable UUID notificationId) {
        notificationService.markRead(jwtTokenUtil.getCurrentUserId(), notificationId);
        return ok(SuccessResponseConstant.NOTIFICATION_READ, null);
    }

    @PostMapping("/read-all")
    public ResponseEntity<GlobalApiResponse<Void>> markAllRead() {
        notificationService.markAllRead(jwtTokenUtil.getCurrentUserId());
        return ok(SuccessResponseConstant.NOTIFICATIONS_READ_ALL, null);
    }
}

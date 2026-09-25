package io.github.mainalisandeep.cvgen.controller;

import io.github.mainalisandeep.cvgen.common.controller.BaseController;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.common.message.SuccessResponseConstant;
import io.github.mainalisandeep.cvgen.common.response.GlobalApiResponse;
import io.github.mainalisandeep.cvgen.dto.AdminNotificationResponseDto;
import io.github.mainalisandeep.cvgen.dto.NotificationCreateRequestDto;
import io.github.mainalisandeep.cvgen.dto.PageResponseDto;
import io.github.mainalisandeep.cvgen.security.AdminAccessGuard;
import io.github.mainalisandeep.cvgen.security.util.JwtTokenUtil;
import io.github.mainalisandeep.cvgen.service.AdminNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/notifications")
@PreAuthorize(AdminAccessGuard.IS_ACTIVE_ADMIN)
@RequiredArgsConstructor
public class AdminNotificationController extends BaseController {

    private final AdminNotificationService adminNotificationService;
    private final JwtTokenUtil jwtTokenUtil;

    @GetMapping
    public ResponseEntity<GlobalApiResponse<PageResponseDto<AdminNotificationResponseDto>>> listNotifications(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ok(SuccessResponseConstant.FETCH_SUCCESS, adminNotificationService.list(pageable),
                FieldConstantValue.NOTIFICATIONS);
    }

    @PostMapping
    public ResponseEntity<GlobalApiResponse<AdminNotificationResponseDto>> sendNotification(
            @Valid @RequestBody NotificationCreateRequestDto request
    ) {
        return respond(HttpStatus.CREATED, SuccessResponseConstant.NOTIFICATION_SENT,
                adminNotificationService.send(jwtTokenUtil.getCurrentUserId(), request));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<GlobalApiResponse<Void>> deleteNotification(@PathVariable UUID notificationId) {
        adminNotificationService.delete(jwtTokenUtil.getCurrentUserId(), notificationId);
        return ok(SuccessResponseConstant.NOTIFICATION_DELETED, null);
    }
}

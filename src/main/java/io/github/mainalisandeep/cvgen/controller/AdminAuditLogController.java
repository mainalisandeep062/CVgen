package io.github.mainalisandeep.cvgen.controller;

import io.github.mainalisandeep.cvgen.common.controller.BaseController;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.common.message.SuccessResponseConstant;
import io.github.mainalisandeep.cvgen.common.response.GlobalApiResponse;
import io.github.mainalisandeep.cvgen.dto.AdminAuditLogResponseDto;
import io.github.mainalisandeep.cvgen.dto.PageResponseDto;
import io.github.mainalisandeep.cvgen.enums.AdminAction;
import io.github.mainalisandeep.cvgen.security.AdminAccessGuard;
import io.github.mainalisandeep.cvgen.service.AdminAuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only by design: an audit trail nobody can edit is the only kind worth keeping. */
@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize(AdminAccessGuard.IS_ACTIVE_ADMIN)
@RequiredArgsConstructor
public class AdminAuditLogController extends BaseController {

    private final AdminAuditLogService adminAuditLogService;

    @GetMapping
    public ResponseEntity<GlobalApiResponse<PageResponseDto<AdminAuditLogResponseDto>>> listAuditLogs(
            @RequestParam(required = false) AdminAction action,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ok(SuccessResponseConstant.FETCH_SUCCESS, adminAuditLogService.list(action, pageable),
                FieldConstantValue.AUDIT_LOGS);
    }
}

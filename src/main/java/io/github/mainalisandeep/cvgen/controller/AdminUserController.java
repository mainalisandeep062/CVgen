package io.github.mainalisandeep.cvgen.controller;

import io.github.mainalisandeep.cvgen.common.controller.BaseController;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.common.message.SuccessResponseConstant;
import io.github.mainalisandeep.cvgen.common.response.GlobalApiResponse;
import io.github.mainalisandeep.cvgen.dto.AdminUserDetailResponseDto;
import io.github.mainalisandeep.cvgen.dto.AdminUserSummaryResponseDto;
import io.github.mainalisandeep.cvgen.dto.AdminUserUpdateRequestDto;
import io.github.mainalisandeep.cvgen.dto.CreditAdjustmentRequestDto;
import io.github.mainalisandeep.cvgen.dto.CreditTransactionResponseDto;
import io.github.mainalisandeep.cvgen.dto.PageResponseDto;
import io.github.mainalisandeep.cvgen.enums.UserRole;
import io.github.mainalisandeep.cvgen.enums.UserStatus;
import io.github.mainalisandeep.cvgen.security.AdminAccessGuard;
import io.github.mainalisandeep.cvgen.security.util.JwtTokenUtil;
import io.github.mainalisandeep.cvgen.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.UUID;

/**
 * Account administration.
 * <p>
 * The acting admin's id is read here and passed down, like every other controller: the service
 * decides what that id may do to the target, and writes the audit entry with it.
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize(AdminAccessGuard.IS_ACTIVE_ADMIN)
@RequiredArgsConstructor
public class AdminUserController extends BaseController {

    private final AdminUserService adminUserService;
    private final JwtTokenUtil jwtTokenUtil;

    @GetMapping
    public ResponseEntity<GlobalApiResponse<PageResponseDto<AdminUserSummaryResponseDto>>> listUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) UserStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ok(SuccessResponseConstant.FETCH_SUCCESS, adminUserService.search(q, role, status, pageable),
                FieldConstantValue.USERS);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<GlobalApiResponse<AdminUserDetailResponseDto>> getUser(@PathVariable UUID userId) {
        return ok(SuccessResponseConstant.FETCH_SUCCESS, adminUserService.get(userId), FieldConstantValue.USER);
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<GlobalApiResponse<AdminUserDetailResponseDto>> updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminUserUpdateRequestDto request
    ) {
        return ok(SuccessResponseConstant.USER_UPDATED,
                adminUserService.update(jwtTokenUtil.getCurrentUserId(), userId, request));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<GlobalApiResponse<Void>> deleteUser(@PathVariable UUID userId) {
        adminUserService.delete(jwtTokenUtil.getCurrentUserId(), userId);
        return ok(SuccessResponseConstant.USER_DELETED, null);
    }

    @PostMapping("/{userId}/credits")
    public ResponseEntity<GlobalApiResponse<CreditTransactionResponseDto>> adjustCredits(
            @PathVariable UUID userId,
            @Valid @RequestBody CreditAdjustmentRequestDto request
    ) {
        return respond(HttpStatus.CREATED, SuccessResponseConstant.CREDITS_ADJUSTED,
                adminUserService.adjustCredits(jwtTokenUtil.getCurrentUserId(), userId, request));
    }
}

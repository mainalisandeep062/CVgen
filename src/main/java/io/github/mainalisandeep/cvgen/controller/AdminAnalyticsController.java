package io.github.mainalisandeep.cvgen.controller;

import io.github.mainalisandeep.cvgen.common.controller.BaseController;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.common.message.SuccessResponseConstant;
import io.github.mainalisandeep.cvgen.common.response.GlobalApiResponse;
import io.github.mainalisandeep.cvgen.dto.AnalyticsOverviewResponseDto;
import io.github.mainalisandeep.cvgen.security.AdminAccessGuard;
import io.github.mainalisandeep.cvgen.service.AdminAnalyticsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The admin dashboard.
 * <p>
 * Guarded twice, like every controller under {@code /api/admin}: by the role in the token
 * ({@code SecurityConfig}) and by {@link AdminAccessGuard}, which re-reads role and status from the
 * database so a demotion takes effect before the token expires.
 */
@RestController
@RequestMapping("/api/admin/analytics")
@PreAuthorize(AdminAccessGuard.IS_ACTIVE_ADMIN)
@RequiredArgsConstructor
public class AdminAnalyticsController extends BaseController {

    private final AdminAnalyticsService adminAnalyticsService;

    @GetMapping("/overview")
    public ResponseEntity<GlobalApiResponse<AnalyticsOverviewResponseDto>> getOverview(
            @RequestParam(defaultValue = "30")
            @Min(value = 7, message = "{validation.days.range}")
            @Max(value = 365, message = "{validation.days.range}") int days
    ) {
        return ok(SuccessResponseConstant.FETCH_SUCCESS, adminAnalyticsService.overview(days),
                FieldConstantValue.ANALYTICS_OVERVIEW);
    }
}

package io.github.mainalisandeep.cvgen.controller;

import io.github.mainalisandeep.cvgen.common.controller.BaseController;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.common.message.SuccessResponseConstant;
import io.github.mainalisandeep.cvgen.common.response.GlobalApiResponse;
import io.github.mainalisandeep.cvgen.dto.BillingAccountResponseDto;
import io.github.mainalisandeep.cvgen.dto.CreditPackResponseDto;
import io.github.mainalisandeep.cvgen.security.util.JwtTokenUtil;
import io.github.mainalisandeep.cvgen.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * What a signed-in user can see of billing: the packs on sale and their own balance.
 * <p>
 * Read-only until a payment gateway exists - nothing here creates a purchase.
 */
@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController extends BaseController {

    private final BillingService billingService;
    private final JwtTokenUtil jwtTokenUtil;

    @GetMapping("/packs")
    public ResponseEntity<GlobalApiResponse<List<CreditPackResponseDto>>> listPacks() {
        return ok(SuccessResponseConstant.FETCH_SUCCESS, billingService.listActivePacks(), FieldConstantValue.CREDIT_PACKS);
    }

    @GetMapping("/me")
    public ResponseEntity<GlobalApiResponse<BillingAccountResponseDto>> getAccount() {
        return ok(SuccessResponseConstant.FETCH_SUCCESS, billingService.getAccount(jwtTokenUtil.getCurrentUserId()),
                FieldConstantValue.BILLING_ACCOUNT);
    }
}

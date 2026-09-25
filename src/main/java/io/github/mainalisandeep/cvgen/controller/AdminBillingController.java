package io.github.mainalisandeep.cvgen.controller;

import io.github.mainalisandeep.cvgen.common.controller.BaseController;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.common.message.SuccessResponseConstant;
import io.github.mainalisandeep.cvgen.common.response.GlobalApiResponse;
import io.github.mainalisandeep.cvgen.dto.BillingSummaryResponseDto;
import io.github.mainalisandeep.cvgen.dto.CreditPackRequestDto;
import io.github.mainalisandeep.cvgen.dto.CreditPackResponseDto;
import io.github.mainalisandeep.cvgen.dto.CreditTransactionResponseDto;
import io.github.mainalisandeep.cvgen.dto.PageResponseDto;
import io.github.mainalisandeep.cvgen.dto.RefundRequestDto;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionStatus;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionType;
import io.github.mainalisandeep.cvgen.security.AdminAccessGuard;
import io.github.mainalisandeep.cvgen.security.util.JwtTokenUtil;
import io.github.mainalisandeep.cvgen.service.AdminBillingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Revenue reporting, the credit ledger and the credit packs on sale. */
@RestController
@RequestMapping("/api/admin/billing")
@PreAuthorize(AdminAccessGuard.IS_ACTIVE_ADMIN)
@RequiredArgsConstructor
public class AdminBillingController extends BaseController {

    private final AdminBillingService adminBillingService;
    private final JwtTokenUtil jwtTokenUtil;

    @GetMapping("/summary")
    public ResponseEntity<GlobalApiResponse<BillingSummaryResponseDto>> getSummary(
            @RequestParam(defaultValue = "30")
            @Min(value = 7, message = "{validation.days.range}")
            @Max(value = 365, message = "{validation.days.range}") int days
    ) {
        return ok(SuccessResponseConstant.FETCH_SUCCESS, adminBillingService.summary(days),
                FieldConstantValue.BILLING_SUMMARY);
    }

    @GetMapping("/transactions")
    public ResponseEntity<GlobalApiResponse<PageResponseDto<CreditTransactionResponseDto>>> listTransactions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) CreditTransactionType type,
            @RequestParam(required = false) CreditTransactionStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ok(SuccessResponseConstant.FETCH_SUCCESS, adminBillingService.listTransactions(q, type, status, pageable),
                FieldConstantValue.CREDIT_TRANSACTIONS);
    }

    /** 201: the refund is a new ledger row, not an edit of the purchase. */
    @PostMapping("/transactions/{transactionId}/refund")
    public ResponseEntity<GlobalApiResponse<CreditTransactionResponseDto>> refundTransaction(
            @PathVariable UUID transactionId,
            @Valid @RequestBody(required = false) RefundRequestDto request
    ) {
        return respond(HttpStatus.CREATED, SuccessResponseConstant.TRANSACTION_REFUNDED,
                adminBillingService.refund(jwtTokenUtil.getCurrentUserId(), transactionId, request));
    }

    @GetMapping("/packs")
    public ResponseEntity<GlobalApiResponse<List<CreditPackResponseDto>>> listPacks() {
        return ok(SuccessResponseConstant.FETCH_SUCCESS, adminBillingService.listPacks(), FieldConstantValue.CREDIT_PACKS);
    }

    @PostMapping("/packs")
    public ResponseEntity<GlobalApiResponse<CreditPackResponseDto>> createPack(
            @Valid @RequestBody CreditPackRequestDto request
    ) {
        return respond(HttpStatus.CREATED, SuccessResponseConstant.CREDIT_PACK_CREATED,
                adminBillingService.createPack(jwtTokenUtil.getCurrentUserId(), request));
    }

    @PutMapping("/packs/{packId}")
    public ResponseEntity<GlobalApiResponse<CreditPackResponseDto>> updatePack(
            @PathVariable UUID packId,
            @Valid @RequestBody CreditPackRequestDto request
    ) {
        return ok(SuccessResponseConstant.CREDIT_PACK_UPDATED,
                adminBillingService.updatePack(jwtTokenUtil.getCurrentUserId(), packId, request));
    }

    @DeleteMapping("/packs/{packId}")
    public ResponseEntity<GlobalApiResponse<Void>> deletePack(@PathVariable UUID packId) {
        adminBillingService.deletePack(jwtTokenUtil.getCurrentUserId(), packId);
        return ok(SuccessResponseConstant.CREDIT_PACK_DELETED, null);
    }
}

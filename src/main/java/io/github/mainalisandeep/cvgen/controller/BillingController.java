package io.github.mainalisandeep.cvgen.controller;

import io.github.mainalisandeep.cvgen.common.controller.BaseController;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.common.message.SuccessResponseConstant;
import io.github.mainalisandeep.cvgen.common.response.GlobalApiResponse;
import io.github.mainalisandeep.cvgen.dto.BillingAccountResponseDto;
import io.github.mainalisandeep.cvgen.dto.CheckoutRequestDto;
import io.github.mainalisandeep.cvgen.dto.CheckoutResponseDto;
import io.github.mainalisandeep.cvgen.dto.CreditPackResponseDto;
import io.github.mainalisandeep.cvgen.dto.PaymentOrderResponseDto;
import io.github.mainalisandeep.cvgen.enums.PaymentGateway;
import io.github.mainalisandeep.cvgen.security.util.JwtTokenUtil;
import io.github.mainalisandeep.cvgen.service.BillingService;
import io.github.mainalisandeep.cvgen.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * What a signed-in user can do with billing: see the packs on sale and their balance, and buy
 * credits through eSewa or Khalti.
 * <p>
 * A purchase is two calls. {@code POST /checkout} opens an order and says where to send the browser;
 * after the gateway sends the browser back, {@code POST /orders/{id}/confirm} asks the gateway
 * server to server and credits the balance. Nothing the browser brings back is trusted.
 */
@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController extends BaseController {

    private final BillingService billingService;
    private final PaymentService paymentService;
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

    /** Gateways this server can take payments through; the pay button offers only these. */
    @GetMapping("/gateways")
    public ResponseEntity<GlobalApiResponse<List<PaymentGateway>>> listGateways() {
        return ok(SuccessResponseConstant.FETCH_SUCCESS, paymentService.enabledGateways(), FieldConstantValue.PAYMENT_GATEWAYS);
    }

    @PostMapping("/checkout")
    public ResponseEntity<GlobalApiResponse<CheckoutResponseDto>> checkout(@Valid @RequestBody CheckoutRequestDto request) {
        return ok(SuccessResponseConstant.CHECKOUT_STARTED, paymentService.checkout(jwtTokenUtil.getCurrentUserId(), request));
    }

    /** Idempotent: call it as often as the return page likes, credits are granted once. */
    @PostMapping("/orders/{orderId}/confirm")
    public ResponseEntity<GlobalApiResponse<PaymentOrderResponseDto>> confirmOrder(@PathVariable UUID orderId) {
        return ok(SuccessResponseConstant.PAYMENT_CONFIRMED, paymentService.confirm(jwtTokenUtil.getCurrentUserId(), orderId));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<GlobalApiResponse<PaymentOrderResponseDto>> getOrder(@PathVariable UUID orderId) {
        return ok(SuccessResponseConstant.FETCH_SUCCESS, paymentService.get(jwtTokenUtil.getCurrentUserId(), orderId),
                FieldConstantValue.PAYMENT_ORDER);
    }
}

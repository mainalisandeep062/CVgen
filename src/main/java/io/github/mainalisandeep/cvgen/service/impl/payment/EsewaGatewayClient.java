package io.github.mainalisandeep.cvgen.service.impl.payment;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mainalisandeep.cvgen.common.exception.ServiceUnavailableException;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.config.PaymentProperties;
import io.github.mainalisandeep.cvgen.entity.PaymentOrder;
import io.github.mainalisandeep.cvgen.enums.PaymentGateway;
import io.github.mainalisandeep.cvgen.records.GatewayCheckout;
import io.github.mainalisandeep.cvgen.records.GatewayVerdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * eSewa ePay v2.
 * <p>
 * Checkout is a form the browser posts to eSewa, signed with HMAC-SHA256 over
 * {@code total_amount,transaction_uuid,product_code} so the amount cannot be edited on the way.
 * Completion is read from eSewa's status API by the server; the {@code data} eSewa appends to the
 * success URL is never trusted, because the browser carries it.
 */
@Component
public class EsewaGatewayClient implements PaymentGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(EsewaGatewayClient.class);

    static final String SIGNED_FIELDS = "total_amount,transaction_uuid,product_code";

    private final PaymentProperties properties;
    private final RestClient restClient;

    public EsewaGatewayClient(PaymentProperties properties, PaymentHttp paymentHttp) {
        this.properties = properties;
        this.restClient = paymentHttp.client();
    }

    @Override
    public PaymentGateway gateway() {
        return PaymentGateway.ESEWA;
    }

    @Override
    public boolean enabled() {
        return properties.getEsewa().isEnabled();
    }

    @Override
    public GatewayCheckout checkout(PaymentOrder order, String customerName, String customerEmail) {
        PaymentProperties.Esewa esewa = properties.getEsewa();
        String amount = Money.rupees(order.getAmountMinor());
        String transactionUuid = order.getId().toString();
        String returnUrl = properties.getReturnBaseUrl() + "/billing/return/esewa";

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("amount", amount);
        fields.put("tax_amount", "0");
        fields.put("total_amount", amount);
        fields.put("transaction_uuid", transactionUuid);
        fields.put("product_code", esewa.getProductCode());
        fields.put("product_service_charge", "0");
        fields.put("product_delivery_charge", "0");
        fields.put("success_url", returnUrl);
        // eSewa appends nothing to the failure URL, so it carries the order id itself.
        fields.put("failure_url", returnUrl + "?order=" + transactionUuid + "&status=failed");
        fields.put("signed_field_names", SIGNED_FIELDS);
        fields.put("signature", sign("total_amount=" + amount + ",transaction_uuid=" + transactionUuid
                + ",product_code=" + esewa.getProductCode(), esewa.getSecretKey()));

        return new GatewayCheckout(GatewayCheckout.FORM_POST, esewa.getFormUrl(), fields, null);
    }

    @Override
    public GatewayVerdict verify(PaymentOrder order) {
        PaymentProperties.Esewa esewa = properties.getEsewa();
        String uri = UriComponentsBuilder.fromUriString(esewa.getStatusUrl())
                .queryParam("product_code", esewa.getProductCode())
                .queryParam("total_amount", Money.rupees(order.getAmountMinor()))
                .queryParam("transaction_uuid", order.getId().toString())
                .build()
                .toUriString();

        JsonNode body;
        try {
            body = restClient.get().uri(uri).retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("eSewa status check failed for order {}: {}", order.getId(), e.getMessage());
            throw new ServiceUnavailableException(ErrorConstantValue.PAYMENT_GATEWAY_UNAVAILABLE);
        }
        if (body == null) {
            throw new ServiceUnavailableException(ErrorConstantValue.PAYMENT_GATEWAY_UNAVAILABLE);
        }

        String status = body.path("status").asText("");
        String reference = body.path("ref_id").asText(null);
        long paid = Money.minor(body.path("total_amount").asText("-1"));
        return switch (status) {
            case "COMPLETE" -> new GatewayVerdict(GatewayVerdict.Outcome.PAID, reference, paid, status);
            case "PENDING", "AMBIGUOUS" -> new GatewayVerdict(GatewayVerdict.Outcome.PENDING, reference, paid, status);
            // NOT_FOUND: the user left eSewa without paying, or the session expired there.
            case "NOT_FOUND", "CANCELED" -> new GatewayVerdict(GatewayVerdict.Outcome.CANCELED, reference, paid, status);
            default -> new GatewayVerdict(GatewayVerdict.Outcome.FAILED, reference, paid, status);
        };
    }

    static String sign(String message, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}

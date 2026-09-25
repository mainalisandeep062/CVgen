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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Khalti KPG-2 (ePayment).
 * <p>
 * The server initiates the payment and gets a {@code pidx} and a payment URL; the browser is sent
 * there. Completion is read from the lookup API by {@code pidx}, which the server stored at
 * initiation - the query string Khalti returns the browser with is never trusted.
 */
@Component
public class KhaltiGatewayClient implements PaymentGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(KhaltiGatewayClient.class);

    private final PaymentProperties properties;
    private final RestClient restClient;

    public KhaltiGatewayClient(PaymentProperties properties, PaymentHttp paymentHttp) {
        this.properties = properties;
        this.restClient = paymentHttp.client();
    }

    @Override
    public PaymentGateway gateway() {
        return PaymentGateway.KHALTI;
    }

    @Override
    public boolean enabled() {
        return properties.getKhalti().isEnabled();
    }

    @Override
    public GatewayCheckout checkout(PaymentOrder order, String customerName, String customerEmail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("return_url", properties.getReturnBaseUrl() + "/billing/return/khalti");
        body.put("website_url", properties.getReturnBaseUrl());
        body.put("amount", order.getAmountMinor());
        body.put("purchase_order_id", order.getId().toString());
        body.put("purchase_order_name", order.getCredits() + " CVGen credits");
        Map<String, String> customer = new LinkedHashMap<>();
        customer.put("name", customerName == null || customerName.isBlank() ? customerEmail : customerName);
        customer.put("email", customerEmail);
        body.put("customer_info", customer);

        JsonNode response = post("/epayment/initiate/", body, order);
        String pidx = response.path("pidx").asText("");
        String paymentUrl = response.path("payment_url").asText("");
        if (pidx.isBlank() || paymentUrl.isBlank()) {
            log.warn("Khalti initiation for order {} returned no pidx/payment_url: {}", order.getId(), response);
            throw new ServiceUnavailableException(ErrorConstantValue.PAYMENT_GATEWAY_UNAVAILABLE);
        }
        return new GatewayCheckout(GatewayCheckout.REDIRECT, paymentUrl, Map.of(), pidx);
    }

    @Override
    public GatewayVerdict verify(PaymentOrder order) {
        JsonNode response = post("/epayment/lookup/", Map.of("pidx", order.getGatewayReference()), order);

        String status = response.path("status").asText("");
        String reference = response.path("transaction_id").asText(null);
        long paid = response.path("total_amount").asLong(-1);
        return switch (status) {
            case "Completed" -> new GatewayVerdict(GatewayVerdict.Outcome.PAID, reference, paid, status);
            case "Pending", "Initiated" -> new GatewayVerdict(GatewayVerdict.Outcome.PENDING, reference, paid, status);
            case "User canceled" -> new GatewayVerdict(GatewayVerdict.Outcome.CANCELED, reference, paid, status);
            default -> new GatewayVerdict(GatewayVerdict.Outcome.FAILED, reference, paid, status);
        };
    }

    private JsonNode post(String path, Object body, PaymentOrder order) {
        try {
            JsonNode response = restClient.post()
                    .uri(properties.getKhalti().getBaseUrl() + path)
                    .header(HttpHeaders.AUTHORIZATION, "Key " + properties.getKhalti().getSecretKey().strip())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw new ServiceUnavailableException(ErrorConstantValue.PAYMENT_GATEWAY_UNAVAILABLE);
            }
            return response;
        } catch (RestClientException e) {
            log.warn("Khalti call {} failed for order {}: {}", path, order.getId(), e.getMessage());
            throw new ServiceUnavailableException(ErrorConstantValue.PAYMENT_GATEWAY_UNAVAILABLE);
        }
    }
}

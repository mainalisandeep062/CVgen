package io.github.mainalisandeep.cvgen.dto;

import io.github.mainalisandeep.cvgen.enums.PaymentGateway;

import java.util.Map;
import java.util.UUID;

/**
 * Where to send the browser to pay.
 *
 * @param method {@code FORM_POST}: build a form with {@code fields} and submit it to {@code url};
 *               {@code REDIRECT}: {@code window.location = url}
 * @param fields form fields for {@code FORM_POST}, empty otherwise
 */
public record CheckoutResponseDto(
        UUID orderId,
        PaymentGateway gateway,
        String method,
        String url,
        Map<String, String> fields
) {
}

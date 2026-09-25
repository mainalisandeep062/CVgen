package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.dto.CheckoutRequestDto;
import io.github.mainalisandeep.cvgen.dto.CheckoutResponseDto;
import io.github.mainalisandeep.cvgen.dto.PaymentOrderResponseDto;
import io.github.mainalisandeep.cvgen.enums.PaymentGateway;

import java.util.List;
import java.util.UUID;

/**
 * Buying credits through a payment gateway.
 * <p>
 * Credits are granted only on the gateway's own server-to-server answer. Whatever the browser
 * brings back from the gateway identifies the order and nothing more.
 */
public interface PaymentService {

    /** Gateways configured on this server, in display order. */
    List<PaymentGateway> enabledGateways();

    /** Opens an order for the pack and returns how to reach the gateway's payment page. */
    CheckoutResponseDto checkout(UUID userId, CheckoutRequestDto request);

    /**
     * Asks the gateway what happened and moves the order accordingly. Safe to call any number of
     * times: a completed order is returned as it is, never credited twice.
     */
    PaymentOrderResponseDto confirm(UUID userId, UUID orderId);

    PaymentOrderResponseDto get(UUID userId, UUID orderId);
}

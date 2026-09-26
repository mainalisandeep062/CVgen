package io.github.mainalisandeep.cvgen.service.impl.payment;

import io.github.mainalisandeep.cvgen.entity.PaymentOrder;
import io.github.mainalisandeep.cvgen.enums.PaymentGateway;
import io.github.mainalisandeep.cvgen.records.GatewayCheckout;
import io.github.mainalisandeep.cvgen.records.GatewayVerdict;

/**
 * One payment gateway. Implementations talk HTTP and nothing else: they never touch the database,
 * so the service can keep every gateway call outside a transaction.
 */
public interface PaymentGatewayClient {

    PaymentGateway gateway();

    /** Configured with a merchant secret; a disabled gateway is never offered. */
    boolean enabled();

    /** How to send the browser to the gateway for {@code order}. */
    GatewayCheckout checkout(PaymentOrder order, String customerName, String customerEmail);

    /**
     * Asks the gateway, server to server, what happened to {@code order}. This answer - never
     * anything the browser brings back - is what credits are granted on.
     */
    GatewayVerdict verify(PaymentOrder order);
}

package io.github.mainalisandeep.cvgen.enums;

/**
 * Payment gateways a user can buy credits through. Persisted by name in {@code payment_orders.gateway}
 * and written to {@code credit_transactions.payment_method} on the purchase it produces.
 */
public enum PaymentGateway {
    ESEWA,
    KHALTI
}

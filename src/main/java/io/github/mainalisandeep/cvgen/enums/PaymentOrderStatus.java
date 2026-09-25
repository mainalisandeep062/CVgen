package io.github.mainalisandeep.cvgen.enums;

/**
 * Lifecycle of a checkout. Only {@link #PENDING} moves; every other state is final.
 */
public enum PaymentOrderStatus {

    /** Sent to the gateway, not yet confirmed either way. */
    PENDING,

    /** Confirmed server to server; the credits are on the balance. */
    COMPLETED,

    /** The gateway reported the payment as failed, expired or refunded before completion. */
    FAILED,

    /** The user backed out on the gateway's page. */
    CANCELED
}

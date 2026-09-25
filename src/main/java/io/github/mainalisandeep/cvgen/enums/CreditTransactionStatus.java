package io.github.mainalisandeep.cvgen.enums;

/**
 * Lifecycle of a ledger row. Persisted by name in {@code credit_transactions.status}.
 * <p>
 * Only {@link #COMPLETED} rows have moved a balance. {@link #PENDING} and {@link #FAILED} exist for the
 * payment gateway that is not built yet.
 */
public enum CreditTransactionStatus {
    PENDING,
    COMPLETED,
    FAILED,

    /** A completed purchase that a later REFUND row reversed. */
    REFUNDED
}

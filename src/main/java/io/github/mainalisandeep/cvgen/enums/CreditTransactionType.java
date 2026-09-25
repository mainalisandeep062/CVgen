package io.github.mainalisandeep.cvgen.enums;

/**
 * Why a credit balance changed. Persisted by name in {@code credit_transactions.type}.
 */
public enum CreditTransactionType {

    /** Credits bought with money. The only type that counts as revenue. */
    PURCHASE,

    ADMIN_GRANT,

    ADMIN_DEDUCT,

    /** Credits used inside the product, e.g. a premium template. */
    SPEND,

    /** Reversal of a purchase; carries the negated credits and the refunded amount. */
    REFUND
}

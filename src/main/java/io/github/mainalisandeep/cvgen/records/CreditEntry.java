package io.github.mainalisandeep.cvgen.records;

import io.github.mainalisandeep.cvgen.entity.CreditPack;
import io.github.mainalisandeep.cvgen.entity.CreditTransaction;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionStatus;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionType;

import java.util.UUID;

/**
 * Everything about a ledger row except the balance it leaves behind, which only the ledger may
 * compute - under the user's row lock.
 *
 * @param credits        signed delta applied to the balance
 * @param amountMinor    money in minor units, 0 when no money moved
 * @param createdById    acting admin, or {@code null} for a user-initiated change
 * @param createdByEmail snapshot of the acting admin's email
 */
public record CreditEntry(
        CreditTransactionType type,
        CreditTransactionStatus status,
        int credits,
        long amountMinor,
        CreditPack pack,
        String paymentMethod,
        String reference,
        String note,
        CreditTransaction refundOf,
        UUID createdById,
        String createdByEmail
) {
}

package io.github.mainalisandeep.cvgen.dto;

import io.github.mainalisandeep.cvgen.enums.CreditTransactionStatus;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One ledger row.
 *
 * @param credits        signed delta applied to the balance
 * @param balanceAfter   the user's balance once this row was applied
 * @param amountMinor    money in minor units (paisa), 0 when none moved
 * @param createdByEmail acting admin, {@code null} for user-initiated rows
 */
public record CreditTransactionResponseDto(
        UUID id,
        UUID userId,
        String userEmail,
        String userName,
        CreditTransactionType type,
        CreditTransactionStatus status,
        int credits,
        int balanceAfter,
        long amountMinor,
        String currency,
        UUID packId,
        String packName,
        String paymentMethod,
        String reference,
        String note,
        String createdByEmail,
        LocalDateTime createdAt
) {
}

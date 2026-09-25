package io.github.mainalisandeep.cvgen.dto;

import java.util.List;

/**
 * The caller's own credit balance and recent ledger.
 *
 * @param transactions at most 20, newest first
 */
public record BillingAccountResponseDto(
        int balance,
        String currency,
        List<CreditTransactionResponseDto> transactions
) {
}

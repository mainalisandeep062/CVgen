package io.github.mainalisandeep.cvgen.dto;

import io.github.mainalisandeep.cvgen.enums.PaymentGateway;
import io.github.mainalisandeep.cvgen.enums.PaymentOrderStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A checkout as the return page shows it.
 *
 * @param amountMinor paisa
 * @param balance     the caller's credit balance now, so the page can show it without another call
 */
public record PaymentOrderResponseDto(
        UUID id,
        PaymentGateway gateway,
        PaymentOrderStatus status,
        int credits,
        long amountMinor,
        String currency,
        String packName,
        String failureReason,
        int balance,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}

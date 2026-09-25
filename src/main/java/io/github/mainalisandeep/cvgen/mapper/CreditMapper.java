package io.github.mainalisandeep.cvgen.mapper;

import io.github.mainalisandeep.cvgen.dto.CreditPackResponseDto;
import io.github.mainalisandeep.cvgen.dto.CreditTransactionResponseDto;
import io.github.mainalisandeep.cvgen.entity.CreditPack;
import io.github.mainalisandeep.cvgen.entity.CreditTransaction;
import io.github.mainalisandeep.cvgen.entity.User;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Single place where credit packs and ledger rows are translated for the layers above them.
 * <p>
 * Reads the transaction's user and pack associations, so callers either fetch them with the query or
 * map inside a transaction.
 */
@Component
public class CreditMapper {

    public CreditTransactionResponseDto toTransactionDto(CreditTransaction transaction) {
        User user = transaction.getUser();
        CreditPack pack = transaction.getPack();
        return new CreditTransactionResponseDto(
                transaction.getId(),
                user.getId(),
                user.getEmail(),
                user.getName(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getCredits(),
                transaction.getBalanceAfter(),
                transaction.getAmountMinor(),
                transaction.getCurrency(),
                pack == null ? null : pack.getId(),
                pack == null ? null : pack.getName(),
                transaction.getPaymentMethod(),
                transaction.getReference(),
                transaction.getNote(),
                transaction.getCreatedByEmail(),
                toLocalDateTime(transaction.getCreatedAt())
        );
    }

    public CreditPackResponseDto toPackDto(CreditPack pack, long purchases) {
        return new CreditPackResponseDto(
                pack.getId(),
                pack.getName(),
                pack.getCredits(),
                pack.getPriceMinor(),
                pack.getCurrency(),
                pack.isActive(),
                pack.isHighlighted(),
                pack.getSortOrder(),
                purchases,
                toLocalDateTime(pack.getCreatedAt()),
                toLocalDateTime(pack.getUpdatedAt())
        );
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}

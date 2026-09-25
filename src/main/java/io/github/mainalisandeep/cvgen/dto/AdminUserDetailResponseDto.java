package io.github.mainalisandeep.cvgen.dto;

import io.github.mainalisandeep.cvgen.enums.UserRole;
import io.github.mainalisandeep.cvgen.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * {@link AdminUserSummaryResponseDto}'s fields, flat, plus recent activity.
 *
 * @param recentCvs          at most 10, most recently edited first
 * @param recentTransactions at most 10, newest first
 */
public record AdminUserDetailResponseDto(
        UUID userId,
        String email,
        String name,
        UserRole role,
        UserStatus status,
        boolean emailVerified,
        boolean hasPassword,
        List<String> providers,
        String profilePictureUrl,
        long cvCount,
        int creditBalance,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt,
        List<CvSummaryResponseDto> recentCvs,
        List<CreditTransactionResponseDto> recentTransactions
) {
}

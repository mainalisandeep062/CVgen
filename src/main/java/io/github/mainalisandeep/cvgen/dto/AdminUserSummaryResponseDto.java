package io.github.mainalisandeep.cvgen.dto;

import io.github.mainalisandeep.cvgen.enums.UserRole;
import io.github.mainalisandeep.cvgen.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One row of the admin user list.
 *
 * @param hasPassword whether the account can sign in with email + password
 * @param providers   linked OAuth2 providers
 * @param lastLoginAt last token issue, {@code null} if the account never signed in since tracking began
 */
public record AdminUserSummaryResponseDto(
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
        LocalDateTime lastLoginAt
) {
}

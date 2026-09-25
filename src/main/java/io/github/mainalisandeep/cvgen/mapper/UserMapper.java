package io.github.mainalisandeep.cvgen.mapper;

import io.github.mainalisandeep.cvgen.dto.AdminUserDetailResponseDto;
import io.github.mainalisandeep.cvgen.dto.AdminUserSummaryResponseDto;
import io.github.mainalisandeep.cvgen.dto.CreditTransactionResponseDto;
import io.github.mainalisandeep.cvgen.dto.CvSummaryResponseDto;
import io.github.mainalisandeep.cvgen.dto.UserResponseDto;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.entity.UserIdentity;
import io.github.mainalisandeep.cvgen.security.UserPrincipal;
import io.github.mainalisandeep.cvgen.service.impl.FileUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Single place where a {@link User} entity is translated for the layers above it.
 */
@Component
@RequiredArgsConstructor
public class UserMapper {

    private final FileUrlResolver fileUrlResolver;

    /**
     * Authenticated principal for a locally stored user.
     * <p>
     * Must be called inside a transaction: the profile picture is a lazy association, and it is
     * read here so refresh-token rotation keeps the {@code imageUrl} claim it would otherwise drop.
     * <p>
     * Authorities come from the stored role, so every token issued - refresh included - reflects the
     * role as it is now. Tokens already issued keep what they had; admin endpoints re-check the database
     * for that reason.
     */
    public UserPrincipal toPrincipal(User user) {
        List<SimpleGrantedAuthority> authorities = user.getRole().getAuthorities().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return UserPrincipal.localUser(
                user.getId().toString(),
                user.getName(),
                user.getEmail(),
                user.getEmail(),
                user.getPasswordHash(),
                fileUrlResolver.avatarUrl(user.getProfilePictureFile()),
                authorities
        );
    }

    public UserResponseDto toResponseDto(User user, List<UserIdentity> identities) {
        return UserResponseDto.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .createdAt(LocalDateTime.ofInstant(user.getCreatedAt(), ZoneId.systemDefault()))
                .isEmailVerified(user.isEmailVerified())
                .profilePictureUrl(fileUrlResolver.avatarUrl(user.getProfilePictureFile()))
                .providers(identities.stream().map(UserIdentity::getProvider).toList())
                .role(user.getRole())
                .status(user.getStatus())
                .creditBalance(user.getCreditBalance())
                .build();
    }

    public AdminUserSummaryResponseDto toAdminSummaryDto(User user, List<String> providers, long cvCount) {
        return new AdminUserSummaryResponseDto(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getStatus(),
                user.isEmailVerified(),
                user.hasLocalPassword(),
                providers,
                fileUrlResolver.avatarUrl(user.getProfilePictureFile()),
                cvCount,
                user.getCreditBalance(),
                toLocalDateTime(user.getCreatedAt()),
                toLocalDateTime(user.getLastLoginAt())
        );
    }

    public AdminUserDetailResponseDto toAdminDetailDto(
            User user,
            List<String> providers,
            long cvCount,
            List<CvSummaryResponseDto> recentCvs,
            List<CreditTransactionResponseDto> recentTransactions
    ) {
        return new AdminUserDetailResponseDto(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getStatus(),
                user.isEmailVerified(),
                user.hasLocalPassword(),
                providers,
                fileUrlResolver.avatarUrl(user.getProfilePictureFile()),
                cvCount,
                user.getCreditBalance(),
                toLocalDateTime(user.getCreatedAt()),
                toLocalDateTime(user.getLastLoginAt()),
                recentCvs,
                recentTransactions
        );
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}

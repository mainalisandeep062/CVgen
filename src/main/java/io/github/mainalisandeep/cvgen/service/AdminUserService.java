package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.dto.AdminUserDetailResponseDto;
import io.github.mainalisandeep.cvgen.dto.AdminUserSummaryResponseDto;
import io.github.mainalisandeep.cvgen.dto.AdminUserUpdateRequestDto;
import io.github.mainalisandeep.cvgen.dto.CreditAdjustmentRequestDto;
import io.github.mainalisandeep.cvgen.dto.CreditTransactionResponseDto;
import io.github.mainalisandeep.cvgen.dto.PageResponseDto;
import io.github.mainalisandeep.cvgen.enums.UserRole;
import io.github.mainalisandeep.cvgen.enums.UserStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Account administration. Every mutation takes the acting admin's id first and writes an audit entry
 * in the same transaction.
 * <p>
 * Two invariants hold for every mutation: an admin cannot change their own role or status or delete
 * themselves, and there is always at least one active admin left.
 */
public interface AdminUserService {

    /**
     * @param q matched case-insensitively against email and name, contains
     * @throws io.github.mainalisandeep.cvgen.common.exception.BadRequestException when sorting by an
     *                                                                            unsupported property
     */
    PageResponseDto<AdminUserSummaryResponseDto> search(String q, UserRole role, UserStatus status, Pageable pageable);

    AdminUserDetailResponseDto get(UUID userId);

    /** Changing role or status also revokes every refresh token of the user. */
    AdminUserDetailResponseDto update(UUID actorId, UUID userId, AdminUserUpdateRequestDto request);

    /** Hard delete of the account and everything that belongs to it. */
    void delete(UUID actorId, UUID userId);

    /** Positive credits grant, negative deduct. */
    CreditTransactionResponseDto adjustCredits(UUID actorId, UUID userId, CreditAdjustmentRequestDto request);
}

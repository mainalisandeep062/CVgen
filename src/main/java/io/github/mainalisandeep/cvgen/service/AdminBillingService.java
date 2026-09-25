package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.dto.BillingSummaryResponseDto;
import io.github.mainalisandeep.cvgen.dto.CreditPackRequestDto;
import io.github.mainalisandeep.cvgen.dto.CreditPackResponseDto;
import io.github.mainalisandeep.cvgen.dto.CreditTransactionResponseDto;
import io.github.mainalisandeep.cvgen.dto.PageResponseDto;
import io.github.mainalisandeep.cvgen.dto.RefundRequestDto;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionStatus;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionType;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Revenue reporting, the credit ledger and credit packs, as admins see them.
 */
public interface AdminBillingService {

    /** @param days window length in UTC days, today included */
    BillingSummaryResponseDto summary(int days);

    /** Newest first; {@code q} matches the owner's email or name. */
    PageResponseDto<CreditTransactionResponseDto> listTransactions(String q, CreditTransactionType type,
                                                                   CreditTransactionStatus status, Pageable pageable);

    /**
     * Marks a completed purchase REFUNDED and appends a REFUND row reversing its credits.
     *
     * @return the REFUND row
     */
    CreditTransactionResponseDto refund(UUID actorId, UUID transactionId, RefundRequestDto request);

    List<CreditPackResponseDto> listPacks();

    CreditPackResponseDto createPack(UUID actorId, CreditPackRequestDto request);

    CreditPackResponseDto updatePack(UUID actorId, UUID packId, CreditPackRequestDto request);

    /** @throws io.github.mainalisandeep.cvgen.common.exception.ConflictException when any transaction references it */
    void deletePack(UUID actorId, UUID packId);
}

package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.dto.BillingAccountResponseDto;
import io.github.mainalisandeep.cvgen.dto.CreditPackResponseDto;

import java.util.List;
import java.util.UUID;

/**
 * Billing as a signed-in user sees it. Read-only until a payment gateway exists.
 */
public interface BillingService {

    /** Packs on sale, in display order. Purchase counts are not exposed here. */
    List<CreditPackResponseDto> listActivePacks();

    /** The caller's balance and their most recent ledger rows. */
    BillingAccountResponseDto getAccount(UUID userId);
}

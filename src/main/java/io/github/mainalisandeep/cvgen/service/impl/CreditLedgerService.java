package io.github.mainalisandeep.cvgen.service.impl;

import io.github.mainalisandeep.cvgen.common.exception.BadRequestException;
import io.github.mainalisandeep.cvgen.common.exception.ResourceNotFoundException;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.entity.CreditTransaction;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.records.CreditEntry;
import io.github.mainalisandeep.cvgen.repository.CreditTransactionRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The only code that changes a credit balance.
 * <p>
 * Balance and ledger row are written together under a row lock on the user, so two concurrent changes
 * to the same balance serialise instead of both reading the same starting value, and
 * {@code users.credit_balance} always equals the newest row's {@code balance_after}.
 */
@Service
@RequiredArgsConstructor
public class CreditLedgerService {

    private final UserRepository userRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final EntityManager entityManager;

    /**
     * Applies {@code entry} to the user's balance and appends its ledger row.
     * <p>
     * MANDATORY: the ledger row, the balance and the caller's own writes (a refunded purchase, the audit
     * entry) must commit or roll back as one. A caller without a transaction is a bug to surface, not
     * something to paper over by opening a new one here.
     *
     * @throws BadRequestException       when the balance would go below zero
     * @throws ResourceNotFoundException when the user does not exist
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CreditTransaction apply(UUID userId, CreditEntry entry) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.USER));
        // When this transaction had already loaded the user, the lock query hands back that managed copy
        // unchanged, and it may predate a change committed while we waited for the lock. Now that the row
        // is locked, re-reading it is the only read that can be trusted.
        entityManager.refresh(user);

        long balanceAfter = (long) user.getCreditBalance() + entry.credits();
        if (balanceAfter < 0) {
            throw new BadRequestException(ErrorConstantValue.CREDITS_INSUFFICIENT);
        }
        user.setCreditBalance(Math.toIntExact(balanceAfter));

        return creditTransactionRepository.save(CreditTransaction.builder()
                .user(user)
                .type(entry.type())
                .status(entry.status())
                .credits(entry.credits())
                .balanceAfter(user.getCreditBalance())
                .amountMinor(entry.amountMinor())
                .pack(entry.pack())
                .paymentMethod(entry.paymentMethod())
                .reference(entry.reference())
                .note(entry.note())
                .refundOf(entry.refundOf())
                .createdById(entry.createdById())
                .createdByEmail(entry.createdByEmail())
                .build());
    }
}

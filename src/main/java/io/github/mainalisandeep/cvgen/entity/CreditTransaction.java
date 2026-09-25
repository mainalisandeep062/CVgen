package io.github.mainalisandeep.cvgen.entity;

import io.github.mainalisandeep.cvgen.common.entity.BaseEntity;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionStatus;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * One change to a credit balance. Only {@code CreditLedgerService} creates these.
 * <p>
 * The acting admin is kept as a plain id plus an email snapshot rather than an association: the
 * column is SET NULL when that admin is deleted, and nothing ever needs to load them.
 */
@Entity
@Table(name = "credit_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CreditTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private CreditTransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CreditTransactionStatus status;

    /** Signed delta applied to the balance. */
    @Column(name = "credits", nullable = false)
    private int credits;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = CreditPack.DEFAULT_CURRENCY;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pack_id")
    private CreditPack pack;

    @Column(name = "payment_method", length = 32)
    private String paymentMethod;

    @Column(name = "reference", length = 128)
    private String reference;

    @Column(name = "note", length = 500)
    private String note;

    /** On a REFUND row, the purchase it reverses. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_of_id")
    private CreditTransaction refundOf;

    @Column(name = "created_by")
    private UUID createdById;

    @Column(name = "created_by_email", length = 255)
    private String createdByEmail;
}

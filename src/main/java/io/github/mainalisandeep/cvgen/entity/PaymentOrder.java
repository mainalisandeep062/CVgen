package io.github.mainalisandeep.cvgen.entity;

import io.github.mainalisandeep.cvgen.common.entity.BaseEntity;
import io.github.mainalisandeep.cvgen.enums.PaymentGateway;
import io.github.mainalisandeep.cvgen.enums.PaymentOrderStatus;
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

import java.time.Instant;

/**
 * One checkout with a payment gateway. Becomes a PURCHASE ledger row only when the gateway confirms it.
 * <p>
 * {@link #credits} and {@link #amountMinor} are copied from the pack at checkout, so a price change
 * made while the user is paying changes neither what they are charged nor what they receive.
 */
@Entity
@Table(name = "payment_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PaymentOrder extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pack_id")
    private CreditPack pack;

    @Column(name = "credits", nullable = false)
    private int credits;

    /** Minor units (paisa). */
    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = CreditPack.DEFAULT_CURRENCY;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway", nullable = false, length = 16)
    private PaymentGateway gateway;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private PaymentOrderStatus status = PaymentOrderStatus.PENDING;

    /** Khalti's {@code pidx} from initiation; eSewa's {@code ref_id} once confirmed. */
    @Column(name = "gateway_reference", length = 128)
    private String gatewayReference;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private CreditTransaction transaction;

    @Column(name = "completed_at")
    private Instant completedAt;
}

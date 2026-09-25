package io.github.mainalisandeep.cvgen.service.impl;

import io.github.mainalisandeep.cvgen.common.exception.BadRequestException;
import io.github.mainalisandeep.cvgen.common.exception.ResourceNotFoundException;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.dto.CheckoutRequestDto;
import io.github.mainalisandeep.cvgen.dto.CheckoutResponseDto;
import io.github.mainalisandeep.cvgen.dto.PaymentOrderResponseDto;
import io.github.mainalisandeep.cvgen.entity.CreditPack;
import io.github.mainalisandeep.cvgen.entity.PaymentOrder;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionStatus;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionType;
import io.github.mainalisandeep.cvgen.enums.PaymentGateway;
import io.github.mainalisandeep.cvgen.enums.PaymentOrderStatus;
import io.github.mainalisandeep.cvgen.records.CreditEntry;
import io.github.mainalisandeep.cvgen.records.GatewayCheckout;
import io.github.mainalisandeep.cvgen.records.GatewayVerdict;
import io.github.mainalisandeep.cvgen.repository.CreditPackRepository;
import io.github.mainalisandeep.cvgen.repository.PaymentOrderRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.service.PaymentService;
import io.github.mainalisandeep.cvgen.service.impl.payment.PaymentGatewayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Checkout and confirmation, with every gateway call outside a database transaction.
 * <p>
 * A gateway can take seconds to answer. Holding a row lock and a pooled connection across that
 * would let a slow gateway starve the application, so each method is: short transaction, gateway
 * call, short transaction. The second transaction re-reads the order under a row lock and only
 * moves it out of PENDING, which is what makes a replayed or double-clicked confirmation credit once.
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentOrderRepository orderRepository;
    private final CreditPackRepository packRepository;
    private final UserRepository userRepository;
    private final CreditLedgerService creditLedgerService;
    private final Map<PaymentGateway, PaymentGatewayClient> gateways = new EnumMap<>(PaymentGateway.class);
    private final TransactionTemplate transaction;
    private final Clock clock;

    public PaymentServiceImpl(PaymentOrderRepository orderRepository,
                              CreditPackRepository packRepository,
                              UserRepository userRepository,
                              CreditLedgerService creditLedgerService,
                              List<PaymentGatewayClient> clients,
                              PlatformTransactionManager transactionManager,
                              Clock clock) {
        this.orderRepository = orderRepository;
        this.packRepository = packRepository;
        this.userRepository = userRepository;
        this.creditLedgerService = creditLedgerService;
        clients.forEach(client -> gateways.put(client.gateway(), client));
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public List<PaymentGateway> enabledGateways() {
        return Arrays.stream(PaymentGateway.values())
                .filter(gateway -> gateways.containsKey(gateway) && gateways.get(gateway).enabled())
                .toList();
    }

    @Override
    public CheckoutResponseDto checkout(UUID userId, CheckoutRequestDto request) {
        PaymentGatewayClient client = gateways.get(request.getGateway());
        if (client == null || !client.enabled()) {
            throw new BadRequestException(ErrorConstantValue.PAYMENT_GATEWAY_DISABLED);
        }

        record Opened(PaymentOrder order, String name, String email) {
        }
        Opened opened = transaction.execute(status -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.USER));
            CreditPack pack = packRepository.findById(request.getPackId())
                    .filter(CreditPack::isActive)
                    .orElseThrow(() -> new BadRequestException(ErrorConstantValue.PAYMENT_PACK_UNAVAILABLE));
            PaymentOrder order = orderRepository.save(PaymentOrder.builder()
                    .user(user)
                    .pack(pack)
                    .credits(pack.getCredits())
                    .amountMinor(pack.getPriceMinor())
                    .currency(pack.getCurrency())
                    .gateway(request.getGateway())
                    .build());
            return new Opened(order, user.getName(), user.getEmail());
        });

        GatewayCheckout checkout;
        try {
            checkout = client.checkout(opened.order(), opened.name(), opened.email());
        } catch (RuntimeException e) {
            // The order never reached the gateway; close it so it does not linger as PENDING.
            transaction.executeWithoutResult(status -> orderRepository.findByIdForUpdate(opened.order().getId())
                    .ifPresent(order -> fail(order, PaymentOrderStatus.FAILED, "Checkout could not be started")));
            throw e;
        }

        if (checkout.reference() != null) {
            transaction.executeWithoutResult(status -> orderRepository.findByIdForUpdate(opened.order().getId())
                    .ifPresent(order -> order.setGatewayReference(checkout.reference())));
        }

        return new CheckoutResponseDto(opened.order().getId(), request.getGateway(), checkout.method(),
                checkout.url(), checkout.fields());
    }

    @Override
    public PaymentOrderResponseDto confirm(UUID userId, UUID orderId) {
        PaymentOrder snapshot = transaction.execute(status -> orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.PAYMENT_ORDER)));

        if (snapshot.getStatus() != PaymentOrderStatus.PENDING) {
            return get(userId, orderId);
        }

        PaymentGatewayClient client = gateways.get(snapshot.getGateway());
        if (client == null || !client.enabled()) {
            throw new BadRequestException(ErrorConstantValue.PAYMENT_GATEWAY_DISABLED);
        }
        // A Khalti order that never got its pidx never reached Khalti; there is nothing to ask about.
        GatewayVerdict verdict = snapshot.getGateway() == PaymentGateway.KHALTI && snapshot.getGatewayReference() == null
                ? new GatewayVerdict(GatewayVerdict.Outcome.FAILED, null, 0, "Not initiated")
                : client.verify(snapshot);

        transaction.executeWithoutResult(status -> settle(orderId, verdict));
        return get(userId, orderId);
    }

    @Override
    public PaymentOrderResponseDto get(UUID userId, UUID orderId) {
        return transaction.execute(status -> {
            PaymentOrder order = orderRepository.findByIdAndUserId(orderId, userId)
                    .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.PAYMENT_ORDER));
            return toDto(order);
        });
    }

    /** Runs inside a transaction, with the order locked. Only a PENDING order moves. */
    private void settle(UUID orderId, GatewayVerdict verdict) {
        PaymentOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.PAYMENT_ORDER));
        if (order.getStatus() != PaymentOrderStatus.PENDING) {
            return;
        }

        switch (verdict.outcome()) {
            case PENDING -> {
                // Leave it; the return page asks again.
            }
            case CANCELED -> fail(order, PaymentOrderStatus.CANCELED, verdict.detail());
            case FAILED -> fail(order, PaymentOrderStatus.FAILED, verdict.detail());
            case PAID -> {
                if (verdict.amountMinor() != order.getAmountMinor()) {
                    // Never credit on a mismatch; this needs a human, not a retry.
                    log.error("Payment order {} paid {} but was issued for {}", order.getId(),
                            verdict.amountMinor(), order.getAmountMinor());
                    fail(order, PaymentOrderStatus.FAILED, "Amount mismatch");
                    return;
                }
                if (order.getGateway() == PaymentGateway.ESEWA && verdict.reference() != null) {
                    order.setGatewayReference(verdict.reference());
                }
                order.setTransaction(creditLedgerService.apply(order.getUser().getId(), new CreditEntry(
                        CreditTransactionType.PURCHASE,
                        CreditTransactionStatus.COMPLETED,
                        order.getCredits(),
                        order.getAmountMinor(),
                        order.getPack(),
                        order.getGateway().name(),
                        verdict.reference(),
                        null,
                        null,
                        null,
                        null
                )));
                order.setStatus(PaymentOrderStatus.COMPLETED);
                order.setCompletedAt(Instant.now(clock));
            }
        }
    }

    private void fail(PaymentOrder order, PaymentOrderStatus status, String reason) {
        order.setStatus(status);
        order.setFailureReason(reason == null || reason.length() <= 255 ? reason : reason.substring(0, 255));
    }

    private PaymentOrderResponseDto toDto(PaymentOrder order) {
        return new PaymentOrderResponseDto(
                order.getId(),
                order.getGateway(),
                order.getStatus(),
                order.getCredits(),
                order.getAmountMinor(),
                order.getCurrency(),
                order.getPack() == null ? null : order.getPack().getName(),
                order.getFailureReason(),
                order.getUser().getCreditBalance(),
                local(order.getCreatedAt()),
                local(order.getCompletedAt())
        );
    }

    private static LocalDateTime local(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}

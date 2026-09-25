package io.github.mainalisandeep.cvgen.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mainalisandeep.cvgen.common.exception.ConflictException;
import io.github.mainalisandeep.cvgen.common.exception.ResourceNotFoundException;
import io.github.mainalisandeep.cvgen.common.message.ActivityMessageConstant;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.dto.BillingSummaryResponseDto;
import io.github.mainalisandeep.cvgen.dto.CreditPackRequestDto;
import io.github.mainalisandeep.cvgen.dto.CreditPackResponseDto;
import io.github.mainalisandeep.cvgen.dto.CreditTransactionResponseDto;
import io.github.mainalisandeep.cvgen.dto.PageResponseDto;
import io.github.mainalisandeep.cvgen.dto.RefundRequestDto;
import io.github.mainalisandeep.cvgen.entity.CreditPack;
import io.github.mainalisandeep.cvgen.entity.CreditTransaction;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.enums.AdminAction;
import io.github.mainalisandeep.cvgen.enums.AdminTargetType;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionStatus;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionType;
import io.github.mainalisandeep.cvgen.mapper.CreditMapper;
import io.github.mainalisandeep.cvgen.records.CreditEntry;
import io.github.mainalisandeep.cvgen.repository.CreditPackRepository;
import io.github.mainalisandeep.cvgen.repository.CreditTransactionRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.service.AdminAuditLogService;
import io.github.mainalisandeep.cvgen.service.AdminBillingService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminBillingServiceImpl implements AdminBillingService {

    private final CreditTransactionRepository creditTransactionRepository;
    private final CreditPackRepository creditPackRepository;
    private final UserRepository userRepository;
    private final CreditLedgerService creditLedgerService;
    private final AdminAuditLogService adminAuditLogService;
    private final CreditMapper creditMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public BillingSummaryResponseDto summary(int days) {
        ReportingWindow window = ReportingWindow.lastDays(days);
        CreditTransactionRepository.BillingTotals totals = creditTransactionRepository.billingTotals(window.from(), window.to());

        return new BillingSummaryResponseDto(
                days,
                CreditPack.DEFAULT_CURRENCY,
                totals.getRevenueMinor(),
                totals.getRefundsMinor(),
                totals.getPurchases(),
                totals.getPurchases() == 0 ? 0 : totals.getRevenueMinor() / totals.getPurchases(),
                totals.getCreditsSold(),
                totals.getCreditsGranted(),
                totals.getCreditsDeducted(),
                totals.getCreditsSpent(),
                creditTransactionRepository.revenueSeries(window.firstDay(), window.lastDay(), window.from(), window.to()).stream()
                        .map(day -> new BillingSummaryResponseDto.SeriesPoint(
                                LocalDate.parse(day.getDate()), day.getRevenueMinor(), day.getPurchases()))
                        .toList(),
                creditTransactionRepository.topPacks(window.from(), window.to()).stream()
                        .map(pack -> new BillingSummaryResponseDto.TopPack(
                                pack.getPackId(), pack.getName(), pack.getPurchases(), pack.getRevenueMinor()))
                        .toList(),
                creditTransactionRepository.paymentMethods(window.from(), window.to()).stream()
                        .map(method -> new BillingSummaryResponseDto.PaymentMethodStat(
                                method.getMethod(), method.getPurchases(), method.getRevenueMinor()))
                        .toList()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<CreditTransactionResponseDto> listTransactions(String q, CreditTransactionType type,
                                                                          CreditTransactionStatus status, Pageable pageable) {
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<CreditTransaction> page = creditTransactionRepository.findAll(transactionSpecification(q, type, status), newestFirst);
        return PageResponseDto.of(page, page.getContent().stream().map(creditMapper::toTransactionDto).toList());
    }

    @Override
    @Transactional
    public CreditTransactionResponseDto refund(UUID actorId, UUID transactionId, RefundRequestDto request) {
        // Locked, so two admins pressing refund at once cannot both see COMPLETED.
        CreditTransaction purchase = creditTransactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.CREDIT_TRANSACTION));

        if (purchase.getType() != CreditTransactionType.PURCHASE || purchase.getStatus() != CreditTransactionStatus.COMPLETED) {
            throw new ConflictException(ErrorConstantValue.TRANSACTION_NOT_REFUNDABLE);
        }

        String note = request == null || request.getNote() == null || request.getNote().isBlank()
                ? null
                : request.getNote().trim();

        // The ledger refuses a refund that would take the balance negative - credits already spent cannot
        // be taken back - and the whole transaction, status change included, rolls back with it.
        CreditTransaction refund = creditLedgerService.apply(purchase.getUser().getId(), new CreditEntry(
                CreditTransactionType.REFUND,
                CreditTransactionStatus.COMPLETED,
                -purchase.getCredits(),
                purchase.getAmountMinor(),
                purchase.getPack(),
                purchase.getPaymentMethod(),
                null,
                note,
                purchase,
                actorId,
                userRepository.findEmailById(actorId).orElse(null)
        ));
        purchase.setStatus(CreditTransactionStatus.REFUNDED);

        User owner = refund.getUser();
        adminAuditLogService.record(actorId, AdminAction.TRANSACTION_REFUNDED, AdminTargetType.TRANSACTION, purchase.getId(),
                objectMapper.createObjectNode()
                        .put("refundTransactionId", refund.getId().toString())
                        .put("userId", owner.getId().toString())
                        .put("credits", purchase.getCredits())
                        .put("amountMinor", purchase.getAmountMinor())
                        .put("note", note),
                ActivityMessageConstant.AUDIT_TRANSACTION_REFUNDED, purchase.getCredits(), owner.getEmail());

        return creditMapper.toTransactionDto(refund);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreditPackResponseDto> listPacks() {
        Map<UUID, Long> purchases = purchasesByPack();
        return creditPackRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(pack -> creditMapper.toPackDto(pack, purchases.getOrDefault(pack.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional
    public CreditPackResponseDto createPack(UUID actorId, CreditPackRequestDto request) {
        CreditPack pack = new CreditPack();
        applyPackFields(pack, request);
        creditPackRepository.save(pack);

        adminAuditLogService.record(actorId, AdminAction.PACK_CREATED, AdminTargetType.CREDIT_PACK, pack.getId(),
                packDetails(pack), ActivityMessageConstant.AUDIT_PACK_CREATED, pack.getName());
        return creditMapper.toPackDto(pack, 0L);
    }

    /**
     * A price change does not rewrite history: past purchases carry their own amount_minor, so revenue
     * reporting stays correct after a pack is repriced.
     */
    @Override
    @Transactional
    public CreditPackResponseDto updatePack(UUID actorId, UUID packId, CreditPackRequestDto request) {
        CreditPack pack = findPack(packId);
        applyPackFields(pack, request);

        adminAuditLogService.record(actorId, AdminAction.PACK_UPDATED, AdminTargetType.CREDIT_PACK, pack.getId(),
                packDetails(pack), ActivityMessageConstant.AUDIT_PACK_UPDATED, pack.getName());
        return creditMapper.toPackDto(pack, purchasesByPack().getOrDefault(pack.getId(), 0L));
    }

    @Override
    @Transactional
    public void deletePack(UUID actorId, UUID packId) {
        CreditPack pack = findPack(packId);

        // Any status counts: even a failed purchase is history that names this pack.
        if (creditTransactionRepository.existsByPackId(packId)) {
            throw new ConflictException(ErrorConstantValue.CREDIT_PACK_IN_USE);
        }

        adminAuditLogService.record(actorId, AdminAction.PACK_DELETED, AdminTargetType.CREDIT_PACK, pack.getId(),
                packDetails(pack), ActivityMessageConstant.AUDIT_PACK_DELETED, pack.getName());
        creditPackRepository.delete(pack);
    }

    private void applyPackFields(CreditPack pack, CreditPackRequestDto request) {
        pack.setName(request.getName().trim());
        pack.setCredits(request.getCredits());
        pack.setPriceMinor(request.getPriceMinor());
        pack.setCurrency(CreditPack.DEFAULT_CURRENCY);
        pack.setActive(request.getActive() == null || request.getActive());
        pack.setHighlighted(Boolean.TRUE.equals(request.getHighlighted()));
        pack.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
    }

    private Map<UUID, Long> purchasesByPack() {
        return creditTransactionRepository.countByPack(CreditTransactionType.PURCHASE, CreditTransactionStatus.COMPLETED).stream()
                .collect(Collectors.toMap(CreditTransactionRepository.PackCount::getPackId, CreditTransactionRepository.PackCount::getCount));
    }

    private Specification<CreditTransaction> transactionSpecification(String q, CreditTransactionType type,
                                                                      CreditTransactionStatus status) {
        String pattern = SearchPatterns.containsIgnoreCase(q);
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (pattern != null) {
                Join<CreditTransaction, User> user = root.join("user");
                predicates.add(cb.or(
                        cb.like(cb.lower(user.get("email")), pattern, SearchPatterns.ESCAPE),
                        cb.like(cb.lower(user.get("name")), pattern, SearchPatterns.ESCAPE)));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private JsonNode packDetails(CreditPack pack) {
        return objectMapper.createObjectNode()
                .put("name", pack.getName())
                .put("credits", pack.getCredits())
                .put("priceMinor", pack.getPriceMinor())
                .put("active", pack.isActive());
    }

    private CreditPack findPack(UUID packId) {
        return creditPackRepository.findById(packId)
                .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.CREDIT_PACK));
    }
}

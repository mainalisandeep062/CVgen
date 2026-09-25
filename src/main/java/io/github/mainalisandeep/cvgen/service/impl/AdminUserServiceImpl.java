package io.github.mainalisandeep.cvgen.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mainalisandeep.cvgen.common.exception.BadRequestException;
import io.github.mainalisandeep.cvgen.common.exception.ConflictException;
import io.github.mainalisandeep.cvgen.common.exception.ResourceNotFoundException;
import io.github.mainalisandeep.cvgen.common.message.ActivityMessageConstant;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.dto.AdminUserDetailResponseDto;
import io.github.mainalisandeep.cvgen.dto.AdminUserSummaryResponseDto;
import io.github.mainalisandeep.cvgen.dto.AdminUserUpdateRequestDto;
import io.github.mainalisandeep.cvgen.dto.CreditAdjustmentRequestDto;
import io.github.mainalisandeep.cvgen.dto.CreditTransactionResponseDto;
import io.github.mainalisandeep.cvgen.dto.CvSummaryResponseDto;
import io.github.mainalisandeep.cvgen.dto.PageResponseDto;
import io.github.mainalisandeep.cvgen.entity.CreditTransaction;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.entity.UserIdentity;
import io.github.mainalisandeep.cvgen.enums.AdminAction;
import io.github.mainalisandeep.cvgen.enums.AdminTargetType;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionStatus;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionType;
import io.github.mainalisandeep.cvgen.enums.UserRole;
import io.github.mainalisandeep.cvgen.enums.UserStatus;
import io.github.mainalisandeep.cvgen.mapper.CreditMapper;
import io.github.mainalisandeep.cvgen.mapper.CvMapper;
import io.github.mainalisandeep.cvgen.mapper.UserMapper;
import io.github.mainalisandeep.cvgen.records.CreditEntry;
import io.github.mainalisandeep.cvgen.repository.CreditTransactionRepository;
import io.github.mainalisandeep.cvgen.repository.CvRepository;
import io.github.mainalisandeep.cvgen.repository.NotificationReadRepository;
import io.github.mainalisandeep.cvgen.repository.NotificationRepository;
import io.github.mainalisandeep.cvgen.repository.RefreshTokenRepository;
import io.github.mainalisandeep.cvgen.repository.TrustedDeviceRepository;
import io.github.mainalisandeep.cvgen.repository.UserIdentityRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.service.AdminAuditLogService;
import io.github.mainalisandeep.cvgen.service.AdminUserService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    /** Whitelisted so an arbitrary {@code sort} cannot reach the password hash or become a 500. */
    private static final Set<String> SORTABLE_PROPERTIES = Set.of("createdAt", "lastLoginAt", "email", "name", "creditBalance");
    private static final int RECENT_LIMIT = 10;

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final TrustedDeviceRepository trustedDeviceRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CvRepository cvRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationReadRepository notificationReadRepository;
    private final RefreshTokenService refreshTokenService;
    private final CreditLedgerService creditLedgerService;
    private final AdminAuditLogService adminAuditLogService;
    private final UserMapper userMapper;
    private final CvMapper cvMapper;
    private final CreditMapper creditMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<AdminUserSummaryResponseDto> search(String q, UserRole role, UserStatus status, Pageable pageable) {
        Page<User> page = userRepository.findAll(searchSpecification(q, role, status), withSupportedSort(pageable));
        List<UUID> userIds = page.getContent().stream().map(User::getId).toList();

        // Two batch queries for the whole page instead of two per row.
        Map<UUID, List<String>> providers = providersByUser(userIds);
        Map<UUID, Long> cvCounts = cvCountsByUser(userIds);

        return PageResponseDto.of(page, page.getContent().stream()
                .map(user -> userMapper.toAdminSummaryDto(user,
                        providers.getOrDefault(user.getId(), List.of()),
                        cvCounts.getOrDefault(user.getId(), 0L)))
                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserDetailResponseDto get(UUID userId) {
        return toDetailDto(findUser(userId));
    }

    @Override
    @Transactional
    public AdminUserDetailResponseDto update(UUID actorId, UUID userId, AdminUserUpdateRequestDto request) {
        User user = findUser(userId);

        UserRole targetRole = request.getRole() == null ? user.getRole() : request.getRole();
        UserStatus targetStatus = request.getStatus() == null ? user.getStatus() : request.getStatus();
        boolean roleChanges = targetRole != user.getRole();
        boolean statusChanges = targetStatus != user.getStatus();

        // Re-sending the current values is not a change, so an edit form that round-trips every field
        // still works on the admin's own row.
        if ((roleChanges || statusChanges) && user.getId().equals(actorId)) {
            throw new BadRequestException(ErrorConstantValue.ADMIN_SELF_MODIFICATION);
        }
        if (user.isActiveAdmin() && (targetRole != UserRole.ADMIN || targetStatus != UserStatus.ACTIVE)) {
            requireAnotherActiveAdmin(user);
        }

        if (request.getName() != null && !request.getName().trim().equals(user.getName())) {
            String name = request.getName().trim();
            adminAuditLogService.record(actorId, AdminAction.USER_UPDATED, AdminTargetType.USER, user.getId(),
                    change("name", user.getName(), name),
                    ActivityMessageConstant.AUDIT_USER_RENAMED, user.getEmail(), name);
            user.setName(name);
        }
        if (roleChanges) {
            adminAuditLogService.record(actorId, AdminAction.USER_ROLE_CHANGED, AdminTargetType.USER, user.getId(),
                    change("role", user.getRole().name(), targetRole.name()),
                    ActivityMessageConstant.AUDIT_USER_ROLE_CHANGED, user.getEmail(), user.getRole(), targetRole);
            user.setRole(targetRole);
        }
        if (statusChanges) {
            adminAuditLogService.record(actorId, AdminAction.USER_STATUS_CHANGED, AdminTargetType.USER, user.getId(),
                    change("status", user.getStatus().name(), targetStatus.name()),
                    targetStatus == UserStatus.SUSPENDED
                            ? ActivityMessageConstant.AUDIT_USER_SUSPENDED
                            : ActivityMessageConstant.AUDIT_USER_REACTIVATED,
                    user.getEmail());
            user.setStatus(targetStatus);
        }

        // Every open session has to sign in again. For a suspension that is the point; for a role change
        // it stops a session from carrying on with authorities that no longer match the account.
        if (roleChanges || statusChanges) {
            refreshTokenService.revokeAllForUser(user.getId());
        }

        return toDetailDto(user);
    }

    @Override
    @Transactional
    public void delete(UUID actorId, UUID userId) {
        User user = findUser(userId);

        if (user.getId().equals(actorId)) {
            throw new BadRequestException(ErrorConstantValue.ADMIN_SELF_MODIFICATION);
        }
        if (user.isActiveAdmin()) {
            requireAnotherActiveAdmin(user);
        }

        adminAuditLogService.record(actorId, AdminAction.USER_DELETED, AdminTargetType.USER, user.getId(),
                objectMapper.createObjectNode().put("email", user.getEmail()).put("role", user.getRole().name()),
                ActivityMessageConstant.AUDIT_USER_DELETED, user.getEmail());

        // Identities and trusted devices must go first: their foreign keys to users do not cascade, so
        // the row delete would fail. Refresh tokens, read receipts and addressed notifications would
        // cascade, but are removed explicitly so the whole footprint of an account is visible here.
        // CVs and the credit ledger cascade with the row. Files the user uploaded are not removed:
        // fk_files_uploaded_by is SET NULL, and blob cleanup needs the storage service after commit.
        userIdentityRepository.deleteAllByUserId(userId);
        trustedDeviceRepository.deleteAllByUserId(userId);
        refreshTokenRepository.deleteAllByUserId(userId);
        notificationReadRepository.deleteAllByUserId(userId);
        notificationRepository.deleteAllByRecipientId(userId);
        userRepository.deleteUserRowById(userId);
    }

    @Override
    @Transactional
    public CreditTransactionResponseDto adjustCredits(UUID actorId, UUID userId, CreditAdjustmentRequestDto request) {
        int credits = request.getCredits();
        boolean grant = credits > 0;

        CreditTransaction transaction = creditLedgerService.apply(userId, new CreditEntry(
                grant ? CreditTransactionType.ADMIN_GRANT : CreditTransactionType.ADMIN_DEDUCT,
                CreditTransactionStatus.COMPLETED,
                credits,
                0L,
                null,
                null,
                null,
                request.getNote().trim(),
                null,
                actorId,
                userRepository.findEmailById(actorId).orElse(null)
        ));

        adminAuditLogService.record(actorId, AdminAction.CREDITS_ADJUSTED, AdminTargetType.USER, userId,
                objectMapper.createObjectNode()
                        .put("transactionId", transaction.getId().toString())
                        .put("credits", credits)
                        .put("balanceAfter", transaction.getBalanceAfter())
                        .put("note", transaction.getNote()),
                grant ? ActivityMessageConstant.AUDIT_CREDITS_GRANTED : ActivityMessageConstant.AUDIT_CREDITS_DEDUCTED,
                Math.abs(credits), transaction.getUser().getEmail());

        return creditMapper.toTransactionDto(transaction);
    }

    /**
     * Refuses a change that would leave no active admin.
     * <p>
     * Through the API the acting admin is itself active and cannot change itself, so a plain count
     * could never reach zero - except when two admins act on each other at the same moment. Locking
     * every active admin serialises exactly that race: the second transaction waits, re-reads the
     * predicate after the first commits, and finds only itself.
     */
    private void requireAnotherActiveAdmin(User target) {
        boolean anotherRemains = userRepository.findAllByRoleAndStatusForUpdate(UserRole.ADMIN, UserStatus.ACTIVE).stream()
                .anyMatch(admin -> !admin.getId().equals(target.getId()));
        if (!anotherRemains) {
            throw new ConflictException(ErrorConstantValue.ADMIN_LAST_ADMIN);
        }
    }

    private AdminUserDetailResponseDto toDetailDto(User user) {
        UUID userId = user.getId();
        List<String> providers = userIdentityRepository.findByUserId(userId).stream()
                .map(UserIdentity::getProvider)
                .toList();
        List<CvSummaryResponseDto> recentCvs = cvRepository.findAllByUserId(userId,
                        PageRequest.of(0, RECENT_LIMIT, Sort.by(Sort.Direction.DESC, "updatedAt")))
                .map(cvMapper::toSummaryDto)
                .getContent();
        List<CreditTransactionResponseDto> recentTransactions = creditTransactionRepository.findAllByUserId(userId,
                        PageRequest.of(0, RECENT_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .map(creditMapper::toTransactionDto)
                .toList();

        return userMapper.toAdminDetailDto(user, providers, cvRepository.countByUserId(userId), recentCvs, recentTransactions);
    }

    private Specification<User> searchSpecification(String q, UserRole role, UserStatus status) {
        String pattern = SearchPatterns.containsIgnoreCase(q);
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (pattern != null) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), pattern, SearchPatterns.ESCAPE),
                        cb.like(cb.lower(root.get("name")), pattern, SearchPatterns.ESCAPE)));
            }
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** The id tie-breaker keeps paging stable when many rows share a sort value. */
    private Pageable withSupportedSort(Pageable pageable) {
        Sort requested = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "createdAt");
        for (Sort.Order order : requested) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new BadRequestException(ErrorConstantValue.SORT_UNSUPPORTED, order.getProperty());
            }
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), requested.and(Sort.by("id")));
    }

    private Map<UUID, List<String>> providersByUser(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userIdentityRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.groupingBy(identity -> identity.getUser().getId(),
                        Collectors.mapping(UserIdentity::getProvider, Collectors.toList())));
    }

    private Map<UUID, Long> cvCountsByUser(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return cvRepository.countByUserIds(userIds).stream()
                .collect(Collectors.toMap(CvRepository.UserCvCount::getUserId, CvRepository.UserCvCount::getCount));
    }

    private ObjectNode change(String field, String from, String to) {
        return objectMapper.createObjectNode().put("field", field).put("from", from).put("to", to);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.USER));
    }
}

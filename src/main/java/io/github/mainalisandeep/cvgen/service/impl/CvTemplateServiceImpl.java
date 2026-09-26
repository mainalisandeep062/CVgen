package io.github.mainalisandeep.cvgen.service.impl;

import io.github.mainalisandeep.cvgen.common.exception.BadRequestException;
import io.github.mainalisandeep.cvgen.common.exception.ForbiddenException;
import io.github.mainalisandeep.cvgen.common.exception.ResourceNotFoundException;
import io.github.mainalisandeep.cvgen.common.message.CustomMessageSource;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.dto.CvTemplateResponseDto;
import io.github.mainalisandeep.cvgen.dto.TemplateUnlockResponseDto;
import io.github.mainalisandeep.cvgen.entity.CreditTransaction;
import io.github.mainalisandeep.cvgen.entity.CvTemplate;
import io.github.mainalisandeep.cvgen.entity.TemplateUnlock;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionStatus;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionType;
import io.github.mainalisandeep.cvgen.mapper.CvTemplateMapper;
import io.github.mainalisandeep.cvgen.records.CreditEntry;
import io.github.mainalisandeep.cvgen.repository.CvTemplateRepository;
import io.github.mainalisandeep.cvgen.repository.TemplateUnlockRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.service.CvTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CvTemplateServiceImpl implements CvTemplateService {

    private static final String UNLOCK_NOTE = "ledger.note.template.unlocked";

    private final CvTemplateRepository cvTemplateRepository;
    private final CvTemplateMapper cvTemplateMapper;
    private final TemplateUnlockRepository templateUnlockRepository;
    private final UserRepository userRepository;
    private final CreditLedgerService creditLedgerService;
    private final CustomMessageSource messages;

    @Override
    @Transactional(readOnly = true)
    public List<CvTemplateResponseDto> list(UUID userId) {
        Set<UUID> unlocked = templateUnlockRepository.findTemplateIdsByUserId(userId);
        return cvTemplateRepository.findAllByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .map(template -> cvTemplateMapper.toPublicDto(template, unlocked.contains(template.getId())))
                .toList();
    }

    /**
     * A key the catalogue does not know would store fine and fail only at render time, far from
     * the request that caused it - so it is refused at the door instead. An inactive template is
     * refused the same way: it is hidden from the picker, so choosing it is not a thing a client can do.
     */
    @Override
    @Transactional(readOnly = true)
    public String requireKnown(String key) {
        return findActive(key).getTemplateKey();
    }

    @Override
    @Transactional(readOnly = true)
    public String requireUsable(UUID userId, String key) {
        CvTemplate template = findActive(key);
        if (template.isPremium() && !templateUnlockRepository.existsByUserIdAndTemplateId(userId, template.getId())) {
            throw new ForbiddenException(ErrorConstantValue.CV_TEMPLATE_LOCKED, template.getName());
        }
        return template.getTemplateKey();
    }

    /**
     * The user row is locked before the "already unlocked?" check, not after: two racing unlocks
     * then run one after the other, and the second sees the first's row and charges nothing. The
     * unique constraint on the pair stays as the backstop.
     */
    @Override
    @Transactional
    public TemplateUnlockResponseDto unlock(UUID userId, String key) {
        CvTemplate template = cvTemplateRepository.findByTemplateKeyAndActiveTrue(key == null ? null : key.trim())
                .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.CV_TEMPLATE));
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.USER));

        boolean owned = !template.isPremium()
                || templateUnlockRepository.existsByUserIdAndTemplateId(userId, template.getId());
        if (!owned) {
            CreditTransaction spend = creditLedgerService.apply(userId, new CreditEntry(
                    CreditTransactionType.SPEND,
                    CreditTransactionStatus.COMPLETED,
                    -template.getCreditCost(),
                    0,
                    null,
                    null,
                    null,
                    messages.get(UNLOCK_NOTE, template.getName()),
                    null,
                    null,
                    null
            ));
            templateUnlockRepository.save(TemplateUnlock.builder()
                    .user(user)
                    .template(template)
                    .credits(template.getCreditCost())
                    .transaction(spend)
                    .build());
        }

        return new TemplateUnlockResponseDto(cvTemplateMapper.toPublicDto(template, true), user.getCreditBalance());
    }

    private CvTemplate findActive(String key) {
        String candidate = key == null ? null : key.trim();
        return cvTemplateRepository.findByTemplateKeyAndActiveTrue(candidate)
                .orElseThrow(() -> new BadRequestException(ErrorConstantValue.CV_TEMPLATE_UNKNOWN, candidate));
    }
}

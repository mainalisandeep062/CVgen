package io.github.mainalisandeep.cvgen.service.impl;

import io.github.mainalisandeep.cvgen.common.exception.ResourceNotFoundException;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.dto.BillingAccountResponseDto;
import io.github.mainalisandeep.cvgen.dto.CreditPackResponseDto;
import io.github.mainalisandeep.cvgen.entity.CreditPack;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.mapper.CreditMapper;
import io.github.mainalisandeep.cvgen.repository.CreditPackRepository;
import io.github.mainalisandeep.cvgen.repository.CreditTransactionRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private static final int RECENT_TRANSACTIONS = 20;

    private final CreditPackRepository creditPackRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final UserRepository userRepository;
    private final CreditMapper creditMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CreditPackResponseDto> listActivePacks() {
        return creditPackRepository.findAllByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .map(pack -> creditMapper.toPackDto(pack, 0L))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BillingAccountResponseDto getAccount(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.USER));

        return new BillingAccountResponseDto(
                user.getCreditBalance(),
                CreditPack.DEFAULT_CURRENCY,
                creditTransactionRepository.findAllByUserId(userId,
                                PageRequest.of(0, RECENT_TRANSACTIONS, Sort.by(Sort.Direction.DESC, "createdAt")))
                        .stream()
                        .map(creditMapper::toTransactionDto)
                        .toList()
        );
    }
}

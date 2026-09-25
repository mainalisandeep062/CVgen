package io.github.mainalisandeep.cvgen.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mainalisandeep.cvgen.common.message.CustomMessageSource;
import io.github.mainalisandeep.cvgen.dto.AdminAuditLogResponseDto;
import io.github.mainalisandeep.cvgen.dto.PageResponseDto;
import io.github.mainalisandeep.cvgen.entity.AdminAuditLog;
import io.github.mainalisandeep.cvgen.enums.AdminAction;
import io.github.mainalisandeep.cvgen.enums.AdminTargetType;
import io.github.mainalisandeep.cvgen.mapper.AdminAuditLogMapper;
import io.github.mainalisandeep.cvgen.repository.AdminAuditLogRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.service.AdminAuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAuditLogServiceImpl implements AdminAuditLogService {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final UserRepository userRepository;
    private final AdminAuditLogMapper adminAuditLogMapper;
    private final CustomMessageSource customMessageSource;

    /** MANDATORY rather than REQUIRED: a new transaction here would commit an entry for a change that may still roll back. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID actorId, AdminAction action, AdminTargetType targetType, UUID targetId,
                       JsonNode details, String summaryKey, Object... summaryArgs) {
        adminAuditLogRepository.save(AdminAuditLog.builder()
                .actorId(actorId)
                .actorEmail(userRepository.findEmailById(actorId).orElse(null))
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .summary(customMessageSource.get(summaryKey, summaryArgs))
                .details(details)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<AdminAuditLogResponseDto> list(AdminAction action, Pageable pageable) {
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<AdminAuditLog> page = action == null
                ? adminAuditLogRepository.findAll(newestFirst)
                : adminAuditLogRepository.findAllByAction(action, newestFirst);
        return PageResponseDto.of(page, page.getContent().stream().map(adminAuditLogMapper::toDto).toList());
    }
}

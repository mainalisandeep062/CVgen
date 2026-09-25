package io.github.mainalisandeep.cvgen.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mainalisandeep.cvgen.dto.AdminAuditLogResponseDto;
import io.github.mainalisandeep.cvgen.dto.PageResponseDto;
import io.github.mainalisandeep.cvgen.enums.AdminAction;
import io.github.mainalisandeep.cvgen.enums.AdminTargetType;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * The admin audit trail: written by every admin mutation, read by the audit screen.
 */
public interface AdminAuditLogService {

    /**
     * Records one admin action inside the caller's transaction, so the entry commits or rolls back
     * with the change it describes. Calling it without a transaction is a programming error.
     *
     * @param summaryKey  {@code ActivityMessageConstant} key, resolved now and stored as text
     * @param summaryArgs arguments for {@code summaryKey}
     */
    void record(UUID actorId, AdminAction action, AdminTargetType targetType, UUID targetId,
                JsonNode details, String summaryKey, Object... summaryArgs);

    /** Newest first; {@code action} null means every action. */
    PageResponseDto<AdminAuditLogResponseDto> list(AdminAction action, Pageable pageable);
}

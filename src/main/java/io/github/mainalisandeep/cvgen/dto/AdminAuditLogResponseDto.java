package io.github.mainalisandeep.cvgen.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mainalisandeep.cvgen.enums.AdminAction;
import io.github.mainalisandeep.cvgen.enums.AdminTargetType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One audit entry.
 *
 * @param actorId {@code null} once the acting admin's account is deleted; {@code actorEmail} survives
 * @param details structured values for the action, or {@code null}
 */
public record AdminAuditLogResponseDto(
        UUID id,
        UUID actorId,
        String actorEmail,
        AdminAction action,
        AdminTargetType targetType,
        UUID targetId,
        String summary,
        JsonNode details,
        LocalDateTime createdAt
) {
}

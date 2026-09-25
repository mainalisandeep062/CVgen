package io.github.mainalisandeep.cvgen.mapper;

import io.github.mainalisandeep.cvgen.dto.AdminAuditLogResponseDto;
import io.github.mainalisandeep.cvgen.entity.AdminAuditLog;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Single place where an {@link AdminAuditLog} is translated for the layers above it.
 */
@Component
public class AdminAuditLogMapper {

    public AdminAuditLogResponseDto toDto(AdminAuditLog entry) {
        return new AdminAuditLogResponseDto(
                entry.getId(),
                entry.getActorId(),
                entry.getActorEmail(),
                entry.getAction(),
                entry.getTargetType(),
                entry.getTargetId(),
                entry.getSummary(),
                entry.getDetails(),
                toLocalDateTime(entry.getCreatedAt())
        );
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}

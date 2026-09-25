package io.github.mainalisandeep.cvgen.entity;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mainalisandeep.cvgen.common.entity.BaseEntity;
import io.github.mainalisandeep.cvgen.enums.AdminAction;
import io.github.mainalisandeep.cvgen.enums.AdminTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * One admin mutation. Ids rather than associations throughout: the entry has to outlive both the
 * actor and the target, and deleting the target is often the action being recorded.
 */
@Entity
@Table(name = "admin_audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AdminAuditLog extends BaseEntity {

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 40)
    private AdminAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private AdminTargetType targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details")
    private JsonNode details;
}

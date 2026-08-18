package io.github.mainalisandeep.cvgen.entity;

import io.github.mainalisandeep.cvgen.common.entity.BaseEntity;
import io.github.mainalisandeep.cvgen.enums.RevocationReason;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    /**
     * The {@code jti} of the JWT handed to the client, and the only way back to this row.
     * <p>
     * Not the primary key: {@code BaseEntity} has Hibernate generate the id, and its
     * {@code UuidGenerator} discards an assigned one, so the id cannot be known before the
     * row is written — while the JWT has to carry it before that.
     */
    @Column(name = "jti", nullable = false, unique = true, updatable = false)
    private UUID jti;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revocation_reason", length = 50)
    private RevocationReason revocationReason;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void markRevoked(RevocationReason reason, Instant now) {
        this.revokedAt = now;
        this.revocationReason = reason;
    }
}

package io.github.mainalisandeep.cvgen.entity;

import io.github.mainalisandeep.cvgen.common.entity.BaseEntity;
import io.github.mainalisandeep.cvgen.enums.UserRole;
import io.github.mainalisandeep.cvgen.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column
    private String name;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    /**
     * The displayed profile picture, whatever its origin.
     * <p>
     * Provider avatars, uploads and presets are all copied into {@code files} before being
     * selected, so every consumer - API, PDF renderer, mail templates - reads one column and
     * never branches on where the image came from. Null means "no picture, render initials".
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_picture_file_id")
    private StoredFile profilePictureFile;

    /**
     * Source of the JWT authorities. The token is only a snapshot of it: admin endpoints re-read this
     * column on every request, so a demotion takes effect before the token expires.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    /**
     * Denormalised from {@code credit_transactions}. Written only by {@code CreditLedgerService}, under
     * a row lock, together with the ledger row that explains the change.
     */
    @Column(name = "credit_balance", nullable = false)
    @Builder.Default
    private int creditBalance = 0;

    /** Last time tokens were issued to this account. A refresh does not count as a login. */
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /**
     * True when the account can authenticate with email + password
     * (an OAuth2-only account has no password hash).
     */
    public boolean hasLocalPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public boolean isActiveAdmin() {
        return role == UserRole.ADMIN && status == UserStatus.ACTIVE;
    }
}

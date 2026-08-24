package io.github.mainalisandeep.cvgen.entity;

import io.github.mainalisandeep.cvgen.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
     * True when the account can authenticate with email + password
     * (an OAuth2-only account has no password hash).
     */
    public boolean hasLocalPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }
}

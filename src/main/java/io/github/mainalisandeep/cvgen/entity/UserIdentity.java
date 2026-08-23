package io.github.mainalisandeep.cvgen.entity;

import io.github.mainalisandeep.cvgen.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "user_identities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class UserIdentity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column(name = "email_at_provider")
    private String emailAtProvider;

    /**
     * Avatar URL this provider last reported, refreshed on every login.
     * <p>
     * A remote pointer, not a file: provider CDNs rotate it and LinkedIn signs it with an
     * expiry, so it is never rendered directly. It is the menu the user picks from, and the
     * source a {@link StoredFile} copy is taken from.
     */
    @Column(name = "avatar_url_at_provider", length = 512)
    private String avatarUrlAtProvider;
}

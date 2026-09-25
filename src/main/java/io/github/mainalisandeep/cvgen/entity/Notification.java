package io.github.mainalisandeep.cvgen.entity;

import io.github.mainalisandeep.cvgen.common.entity.BaseEntity;
import io.github.mainalisandeep.cvgen.enums.NotificationAudience;
import io.github.mainalisandeep.cvgen.enums.NotificationLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * A message from an admin. A broadcast is a single row; who can see it is decided on read, so
 * sending to everyone costs one insert and read receipts are the only per-user rows.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Notification extends BaseEntity {

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "body", nullable = false, length = 2000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 16)
    private NotificationLevel level;

    @Column(name = "link", length = 512)
    private String link;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", nullable = false, length = 8)
    private NotificationAudience audience;

    /** Set exactly when {@link #audience} is USER. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    private User recipient;

    @Column(name = "created_by")
    private UUID createdById;

    @Column(name = "created_by_email", length = 255)
    private String createdByEmail;
}

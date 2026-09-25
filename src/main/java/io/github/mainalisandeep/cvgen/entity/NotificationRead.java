package io.github.mainalisandeep.cvgen.entity;

import io.github.mainalisandeep.cvgen.common.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Read receipt: one user has seen one notification. {@code createdAt} is the read time.
 * <p>
 * A surrogate id plus a unique pair instead of a composite key, so it stays a {@link BaseEntity}.
 * Rows are written with {@code INSERT ... ON CONFLICT DO NOTHING}, which is what makes marking as
 * read idempotent under concurrent requests.
 */
@Entity
@Table(name = "notification_reads", uniqueConstraints = @UniqueConstraint(
        name = "uq_notification_reads_notification_user", columnNames = {"notification_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class NotificationRead extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}

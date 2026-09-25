package io.github.mainalisandeep.cvgen.repository;

import io.github.mainalisandeep.cvgen.entity.NotificationRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Read receipts. Written with {@code ON CONFLICT DO NOTHING} rather than check-then-insert, so two
 * tabs marking the same notification never race into a unique-constraint 500.
 */
@Repository
public interface NotificationReadRepository extends JpaRepository<NotificationRead, UUID> {

    @Query("SELECT r.notification.id FROM NotificationRead r WHERE r.user.id = :userId AND r.notification.id IN :notificationIds")
    List<UUID> findReadNotificationIds(@Param("userId") UUID userId, @Param("notificationIds") Collection<UUID> notificationIds);

    @Query("""
            SELECT r.notification.id AS notificationId, COUNT(r) AS count FROM NotificationRead r
            WHERE r.notification.id IN :notificationIds
            GROUP BY r.notification.id
            """)
    List<ReadCount> countByNotificationIds(@Param("notificationIds") Collection<UUID> notificationIds);

    @Modifying
    @Query(value = """
            INSERT INTO notification_reads (id, notification_id, user_id, created_at, updated_at)
            VALUES (gen_random_uuid(), :notificationId, :userId, now(), now())
            ON CONFLICT (notification_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int markRead(@Param("notificationId") UUID notificationId, @Param("userId") UUID userId);

    /** Same visibility predicate as {@code NotificationRepository.VISIBLE_TO_USER}, in SQL. */
    @Modifying
    @Query(value = """
            INSERT INTO notification_reads (id, notification_id, user_id, created_at, updated_at)
            SELECT gen_random_uuid(), n.id, :userId, now(), now()
            FROM notifications n
            WHERE n.recipient_id = :userId OR (n.audience = 'ALL' AND n.created_at >= :since)
            ON CONFLICT (notification_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int markAllRead(@Param("userId") UUID userId, @Param("since") Instant since);

    @Modifying
    @Query("DELETE FROM NotificationRead r WHERE r.user.id = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);

    interface ReadCount {
        UUID getNotificationId();

        long getCount();
    }
}

package io.github.mainalisandeep.cvgen.repository;

import io.github.mainalisandeep.cvgen.entity.Notification;
import io.github.mainalisandeep.cvgen.enums.NotificationAudience;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Visibility is computed here, never by loading and filtering: a notification is visible to a user
 * when it is addressed to them, or when it is a broadcast created at or after their account.
 * The second half is why a new account does not inherit years of old announcements.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** The visibility predicate, shared by every query below. {@code :since} is the user's createdAt. */
    String VISIBLE_TO_USER = "(n.recipient.id = :userId OR (n.audience = :broadcast AND n.createdAt >= :since))";

    @Override
    @EntityGraph(attributePaths = "recipient")
    Page<Notification> findAll(Pageable pageable);

    @Query(value = "SELECT n FROM Notification n WHERE " + VISIBLE_TO_USER,
            countQuery = "SELECT COUNT(n) FROM Notification n WHERE " + VISIBLE_TO_USER)
    Page<Notification> findVisible(@Param("userId") UUID userId,
                                   @Param("broadcast") NotificationAudience broadcast,
                                   @Param("since") Instant since,
                                   Pageable pageable);

    @Query("SELECT COUNT(n) > 0 FROM Notification n WHERE n.id = :id AND " + VISIBLE_TO_USER)
    boolean isVisible(@Param("id") UUID id,
                      @Param("userId") UUID userId,
                      @Param("broadcast") NotificationAudience broadcast,
                      @Param("since") Instant since);

    @Query("SELECT COUNT(n) FROM Notification n WHERE " + VISIBLE_TO_USER
            + " AND NOT EXISTS (SELECT r.id FROM NotificationRead r WHERE r.notification = n AND r.user.id = :userId)")
    long countUnread(@Param("userId") UUID userId,
                     @Param("broadcast") NotificationAudience broadcast,
                     @Param("since") Instant since);

    /** Account deletion. Their read receipts go with them through fk_notification_reads_notification. */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.recipient.id = :userId")
    int deleteAllByRecipientId(@Param("userId") UUID userId);
}

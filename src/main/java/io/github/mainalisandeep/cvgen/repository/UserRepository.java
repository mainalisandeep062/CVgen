package io.github.mainalisandeep.cvgen.repository;

import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.enums.UserRole;
import io.github.mainalisandeep.cvgen.enums.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    /**
     * The per-request suspension check. A scalar read on the primary key, so it stays one index
     * lookup and never hydrates the entity or its lazy associations.
     */
    @Query("SELECT u.status FROM User u WHERE u.id = :id")
    Optional<UserStatus> findStatusById(@Param("id") UUID id);

    /** Backs the admin guard: role and status as they are now, not as the token remembers them. */
    boolean existsByIdAndRoleAndStatus(UUID id, UserRole role, UserStatus status);

    @Query("SELECT u.email FROM User u WHERE u.id = :id")
    Optional<String> findEmailById(@Param("id") UUID id);

    /**
     * {@code SELECT ... FOR UPDATE} on one user. Every credit balance change goes through this, so two
     * concurrent changes serialise instead of both reading the same balance.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Locks every active admin. Counting alone is not enough: two admins demoting each other at the
     * same moment would each see two admins and both succeed. Under this lock the second transaction
     * waits, re-evaluates the predicate once the first commits, and sees one.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.role = :role AND u.status = :status")
    List<User> findAllByRoleAndStatusForUpdate(@Param("role") UserRole role, @Param("status") UserStatus status);

    /** Bootstrap candidates: listed, verified, and not already holding the role. */
    @Query("SELECT u FROM User u WHERE lower(u.email) IN :emails AND u.emailVerified = true AND u.role <> :role")
    List<User> findVerifiedByLowerEmailInAndRoleNot(@Param("emails") Collection<String> emails, @Param("role") UserRole role);

    /** Newest accounts without a count query, for the activity feed. */
    List<User> findAllBy(Pageable pageable);

    /**
     * Deletes the row itself. Flushes first so pending writes in the same transaction (the audit
     * entry) are not lost, and clears after because the database cascade removes rows the persistence
     * context may still hold.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM User u WHERE u.id = :id")
    void deleteUserRowById(@Param("id") UUID id);
}

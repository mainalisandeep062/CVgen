package io.github.mainalisandeep.cvgen.repository;

import io.github.mainalisandeep.cvgen.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Entry point for every presented token: the JWT carries this value as its {@code jti}. */
    Optional<RefreshToken> findByJti(UUID jti);

    List<RefreshToken> findByFamilyId(UUID familyId);

    List<RefreshToken> findByUserIdAndRevokedAtIsNull(UUID userId);

    /** Sliding window: keep only the N most recent rows per user, hard-delete the rest. */
    @Modifying
    @Query(value = """
            DELETE FROM refresh_tokens
            WHERE user_id = :userId
            AND id NOT IN (
                SELECT id FROM refresh_tokens
                WHERE user_id = :userId
                ORDER BY created_at DESC
                LIMIT :windowSize
            )
            """, nativeQuery = true)
    void pruneToWindow(@Param("userId") UUID userId, @Param("windowSize") int windowSize);

    /**
     * fk_refresh_token_user cascades, so this is not needed for the delete to succeed. Account deletion
     * still removes them explicitly, first, so a token cannot be rotated between the checks and the
     * cascade.
     */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.user.id = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
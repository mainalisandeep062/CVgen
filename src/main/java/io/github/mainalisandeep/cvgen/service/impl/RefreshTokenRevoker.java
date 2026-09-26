package io.github.mainalisandeep.cvgen.service.impl;

import io.github.mainalisandeep.cvgen.entity.RefreshToken;
import io.github.mainalisandeep.cvgen.enums.RevocationReason;
import io.github.mainalisandeep.cvgen.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Revocations that must survive the exception thrown right after them.
 *
 * <p>Reuse detection revokes a family and then rejects the request with an
 * {@link io.github.mainalisandeep.cvgen.common.exception.UnauthorizedException}. That is a
 * {@code RuntimeException}, so the caller's transaction rolls back - including the revocation, which
 * left the thief's token live and no {@code REUSE_DETECTED} row behind. {@code REQUIRES_NEW} commits
 * the revocation in its own transaction before the exception is thrown.
 *
 * <p>A separate bean on purpose: {@code REQUIRES_NEW} is applied by the transactional proxy, and a
 * self-invocation inside {@link RefreshTokenService} bypasses that proxy and silently joins the
 * caller's transaction instead.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenRevoker {

    private final RefreshTokenRepository repo;

    /**
     * Kills the family a replayed token belongs to, keeping the two roles distinguishable in the audit
     * trail: {@code REUSE_DETECTED} marks the token that was actually presented twice, while its still
     * active siblings - the thief's token among them - get {@link RevocationReason#FAMILY_COMPROMISED}.
     *
     * <p>Rows revoked earlier for their own reason ({@code ROTATED}, {@code LOGOUT}) keep it: they were
     * already dead before the replay, and overwriting them would erase the chain's history.
     *
     * @param familyId         family of the replayed token
     * @param replayedTokenId  id of the row whose raw token was presented a second time
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeCompromisedFamily(UUID familyId, UUID replayedTokenId) {
        Instant now = Instant.now();
        for (RefreshToken token : repo.findByFamilyId(familyId)) {
            if (token.getId().equals(replayedTokenId)) {
                // already revoked as ROTATED when it was legitimately used; the replay is the louder fact
                token.setRevocationReason(RevocationReason.REUSE_DETECTED);
                if (token.getRevokedAt() == null) {
                    token.setRevokedAt(now);
                }
            } else if (token.getRevokedAt() == null) {
                token.markRevoked(RevocationReason.FAMILY_COMPROMISED, now);
            }
        }
    }
}

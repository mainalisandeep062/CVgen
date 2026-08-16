package io.github.mainalisandeep.cvgen.service.impl;

import io.github.mainalisandeep.cvgen.common.exception.UnauthorizedException;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.entity.RefreshToken;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.enums.RevocationReason;
import io.github.mainalisandeep.cvgen.mapper.UserMapper;
import io.github.mainalisandeep.cvgen.repository.RefreshTokenRepository;
import io.github.mainalisandeep.cvgen.security.JwtTokenProvider;
import io.github.mainalisandeep.cvgen.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int WINDOW_SIZE = 5;

    private final RefreshTokenRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;
    private final RefreshTokenRevoker revoker;

    @Transactional
    public String issueNewFamily(User user) {
        String rawToken = issue(user, UUID.randomUUID()).rawToken();
        pruneToWindow(user);
        return rawToken;
    }

    @Transactional
    public String rotate(String rawToken) {
        if (rawToken == null || !jwtTokenProvider.validateRefreshToken(rawToken)) {
            throw new UnauthorizedException(ErrorConstantValue.REFRESH_TOKEN_INVALID);
        }

        RefreshToken stored = repo.findByJti(jwtTokenProvider.getTokenIdFromToken(rawToken))
                .orElseThrow(() -> new UnauthorizedException(ErrorConstantValue.REFRESH_TOKEN_INVALID));

        if (!passwordEncoder.matches(rawToken, stored.getTokenHash())) {
            throw new UnauthorizedException(ErrorConstantValue.REFRESH_TOKEN_INVALID);
        }

        if (stored.getRevokedAt() != null) {
            // Already-rotated token presented again = replay/theft. Nuke the whole chain.
            // Committed in its own transaction: the throw below would otherwise roll it back.
            revoker.revokeCompromisedFamily(stored.getFamilyId(), stored.getId());
            throw new UnauthorizedException(ErrorConstantValue.REFRESH_TOKEN_REUSED);
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException(ErrorConstantValue.REFRESH_TOKEN_INVALID);
        }

        IssuedToken issued = issue(stored.getUser(), stored.getFamilyId());

        stored.setRevokedAt(Instant.now());
        stored.setRevocationReason(RevocationReason.ROTATED);
        stored.setLastUsedAt(Instant.now());
        stored.setReplacedById(issued.entity().getId());
        repo.save(stored);

        pruneToWindow(stored.getUser());
        return issued.rawToken();
    }

    @Transactional
    public void revoke(String rawToken, RevocationReason reason) {
        if (rawToken == null || !jwtTokenProvider.validateRefreshToken(rawToken)) {
            return; // nothing to revoke
        }
        repo.findByJti(jwtTokenProvider.getTokenIdFromToken(rawToken)).ifPresent(t -> {
            if (t.getRevokedAt() == null) {
                t.setRevokedAt(Instant.now());
                t.setRevocationReason(reason);
                repo.save(t);
            }
        });
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        repo.findByUserIdAndRevokedAtIsNull(userId).forEach(t -> {
            t.setRevokedAt(Instant.now());
            t.setRevocationReason(RevocationReason.LOGOUT_ALL);
        });
    }

    /** A freshly minted token: the raw JWT for the client, and the row that tracks it. */
    private record IssuedToken(RefreshToken entity, String rawToken) {
    }

    private IssuedToken issue(User user, UUID familyId) {
        UUID jti = UUID.randomUUID();
        UserPrincipal principal = userMapper.toPrincipal(user);
        String rawToken = jwtTokenProvider.generateRefreshToken(principal, jti);

        RefreshToken entity = repo.save(RefreshToken.builder()
                .user(user)
                .familyId(familyId)
                .jti(jti)
                .tokenHash(passwordEncoder.encode(rawToken))
                .expiresAt(Instant.now().plus(JwtTokenProvider.REFRESH_TOKEN_TTL))
                .build());

        return new IssuedToken(entity, rawToken);
    }

    /**
     * Runs last, after every entity change has been flushed: the prune is native SQL, so rows it
     * deletes stay in the persistence context and a later flush of one of them would fail as stale.
     */
    private void pruneToWindow(User user) {
        repo.flush();
        repo.pruneToWindow(user.getId(), WINDOW_SIZE);
    }
}

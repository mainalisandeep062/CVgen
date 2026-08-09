package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.entity.TrustedDevice;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.repository.TrustedDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrustedDeviceService {

    public static final Duration REMEMBER_ME_DURATION = Duration.ofDays(30);
    public static final int TOKEN_BYTES = 32;

    private final TrustedDeviceRepository trustedDeviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Create a new trusted device token for the user.
     * Returns the raw token (caller sets it as an HttpOnly cookie); only the hash is stored.
     */
    @Transactional
    public String remember(User user) {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        trustedDeviceRepository.save(TrustedDevice.builder()
                .user(user)
                .tokenHash(passwordEncoder.encode(rawToken))
                .expiresAt(Instant.now().plus(REMEMBER_ME_DURATION))
                .build());

        return rawToken;
    }

    /**
     * Check whether the raw cookie token matches a non-expired device of this user.
     * Updates last_used_at on a hit (does NOT extend expires_at).
     * <p>
     * The stored token is a one-way hash, so candidates are narrowed by user in SQL and
     * only that handful is compared in memory.
     */
    @Transactional
    public boolean isTrusted(UUID userId, String rawCookieToken) {
        if (rawCookieToken == null || rawCookieToken.isBlank() || userId == null) {
            return false;
        }

        List<TrustedDevice> devices = trustedDeviceRepository.findActiveByUserId(userId, Instant.now());
        for (TrustedDevice device : devices) {
            if (passwordEncoder.matches(rawCookieToken, device.getTokenHash())) {
                device.setLastUsedAt(Instant.now());
                trustedDeviceRepository.save(device);
                return true;
            }
        }

        return false;
    }
}

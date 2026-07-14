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
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrustedDeviceService {

    public static final Duration REMEMBER_ME_DAYS = Duration.ofDays(30);
    public static final int TOKEN_BYTES = 32;
    public static final String COOKIE_NAME = "cvgen_remember_device";

    private final TrustedDeviceRepository trustedDeviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Create a new trusted device token for the user.
     * Returns the raw token (caller sets as HttpOnly cookie).
     */
    @Transactional
    public String remember(User user) {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        String tokenHash = passwordEncoder.encode(rawToken);

        TrustedDevice trustedDevice = TrustedDevice.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(REMEMBER_ME_DAYS))
                .createdAt(Instant.now())
                .build();

        trustedDeviceRepository.save(trustedDevice);
        return rawToken;
    }

    /**
     * Check if the user has a valid trusted device for the given raw cookie token.
     * Updates last_used_at on success (does NOT extend expires_at).
     */
    @Transactional
    public boolean isTrusted(UUID userId, String rawCookieToken) {
        if (rawCookieToken == null || rawCookieToken.isBlank()) {
            return false;
        }

        // We can't query by raw token (it's hashed), so we need to check each active token.
        // In practice, a user will have few trusted devices, so this is acceptable.
        // For a high-scale system, an index on user_id + expires_at would help.
        var devices = trustedDeviceRepository.findAll().stream()
                .filter(d -> d.getUser().getId().equals(userId))
                .filter(d -> d.getExpiresAt().isAfter(Instant.now()))
                .toList();

        for (TrustedDevice device : devices) {
            if (passwordEncoder.matches(rawCookieToken, device.getTokenHash())) {
                device.setLastUsedAt(Instant.now());
                trustedDeviceRepository.save(device);
                return true;
            }
        }

        return false;
    }

    /**
     * Find an existing valid trusted device token for the user.
     * Returns the raw token if found, empty otherwise.
     * Used during login to check if OTP can be skipped.
     */
    @Transactional(readOnly = true)
    public Optional<String> findExistingValidToken(UUID userId, String rawCookieToken) {
        if (rawCookieToken == null || rawCookieToken.isBlank()) {
            return Optional.empty();
        }

        var devices = trustedDeviceRepository.findAll().stream()
                .filter(d -> d.getUser().getId().equals(userId))
                .filter(d -> d.getExpiresAt().isAfter(Instant.now()))
                .toList();

        for (TrustedDevice device : devices) {
            if (passwordEncoder.matches(rawCookieToken, device.getTokenHash())) {
                return Optional.of(rawCookieToken);
            }
        }

        return Optional.empty();
    }
}

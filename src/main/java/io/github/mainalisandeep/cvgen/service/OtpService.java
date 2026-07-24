package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.entity.OtpCode;
import io.github.mainalisandeep.cvgen.enums.OtpPurpose;
import io.github.mainalisandeep.cvgen.repository.OtpCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OtpService {

    public static final int OTP_LENGTH = 6;
    public static final Duration OTP_EXPIRY = Duration.ofMinutes(10);
    public static final int MAX_ATTEMPTS = 5;
    public static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final OtpCodeRepository otpCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate a new OTP for the given email and purpose.
     * Invalidates any prior unconsumed OTP for the same (email, purpose).
     * Returns the raw code (caller passes to MailService, never persists it).
     */
    @Transactional
    public String generate(String email, OtpPurpose purpose) {
        findLatestUnconsumed(email, purpose).ifPresent(existing -> {
            existing.setConsumedAt(Instant.now());
            otpCodeRepository.save(existing);
        });

        String rawCode = generateRawCode();

        otpCodeRepository.save(OtpCode.builder()
                .email(email)
                .purpose(purpose)
                .codeHash(passwordEncoder.encode(rawCode))
                .expiresAt(Instant.now().plus(OTP_EXPIRY))
                .attemptCount(0)
                .build());

        return rawCode;
    }

    /**
     * Verify a raw OTP code for the given email and purpose.
     * Returns true if valid, false otherwise.
     */
    @Transactional
    public boolean verify(String email, OtpPurpose purpose, String rawCode) {
        Optional<OtpCode> latest = findLatestUnconsumed(email, purpose);
        if (latest.isEmpty()) {
            return false;
        }

        OtpCode otpCode = latest.get();
        if (otpCode.isExpired(Instant.now()) || otpCode.getAttemptCount() >= MAX_ATTEMPTS) {
            return false;
        }

        if (!passwordEncoder.matches(rawCode, otpCode.getCodeHash())) {
            otpCode.setAttemptCount(otpCode.getAttemptCount() + 1);
            otpCodeRepository.save(otpCode);
            return false;
        }

        otpCode.setConsumedAt(Instant.now());
        otpCodeRepository.save(otpCode);
        return true;
    }

    /**
     * Check if a resend is allowed (outside the cooldown window).
     */
    @Transactional(readOnly = true)
    public boolean canResend(String email, OtpPurpose purpose) {
        return findLatestUnconsumed(email, purpose)
                .map(otpCode -> !Instant.now().isBefore(otpCode.getCreatedAt().plus(RESEND_COOLDOWN)))
                .orElse(true);
    }

    private Optional<OtpCode> findLatestUnconsumed(String email, OtpPurpose purpose) {
        return otpCodeRepository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(email, purpose);
    }

    private String generateRawCode() {
        StringBuilder code = new StringBuilder(OTP_LENGTH);
        for (int i = 0; i < OTP_LENGTH; i++) {
            code.append(secureRandom.nextInt(10));
        }
        return code.toString();
    }
}

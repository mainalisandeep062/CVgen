package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.entity.OtpCode;
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

    public static final String PURPOSE_SIGNUP = "SIGNUP";
    public static final String PURPOSE_LOGIN = "LOGIN";

    private final OtpCodeRepository otpCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate a new OTP for the given email and purpose.
     * Invalidates any prior unconsumed OTP for the same (email, purpose).
     * Returns the raw code (caller passes to MailService, never persists it).
     */
    @Transactional
    public String generate(String email, String purpose) {
        // Invalidate prior unconsumed OTP
        otpCodeRepository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(email, purpose)
                .ifPresent(existing -> {
                    existing.setConsumedAt(Instant.now());
                    otpCodeRepository.save(existing);
                });

        String rawCode = generateRawCode();
        String codeHash = passwordEncoder.encode(rawCode);

        OtpCode otpCode = OtpCode.builder()
                .email(email)
                .purpose(purpose)
                .codeHash(codeHash)
                .expiresAt(Instant.now().plus(OTP_EXPIRY))
                .attemptCount(0)
                .createdAt(Instant.now())
                .build();

        otpCodeRepository.save(otpCode);
        return rawCode;
    }

    /**
     * Verify a raw OTP code for the given email and purpose.
     * Returns true if valid, false otherwise.
     */
    @Transactional
    public boolean verify(String email, String purpose, String rawCode) {
        Optional<OtpCode> latestOpt = otpCodeRepository
                .findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(email, purpose);

        if (latestOpt.isEmpty()) {
            return false;
        }

        OtpCode otpCode = latestOpt.get();

        // Check expiry
        if (Instant.now().isAfter(otpCode.getExpiresAt())) {
            return false;
        }

        // Check attempt cap
        if (otpCode.getAttemptCount() >= MAX_ATTEMPTS) {
            return false;
        }

        // Verify code against BCrypt hash
        if (!passwordEncoder.matches(rawCode, otpCode.getCodeHash())) {
            otpCode.setAttemptCount(otpCode.getAttemptCount() + 1);
            otpCodeRepository.save(otpCode);
            return false;
        }

        // Success — mark consumed
        otpCode.setConsumedAt(Instant.now());
        otpCodeRepository.save(otpCode);
        return true;
    }

    /**
     * Check if a resend is allowed (outside the cooldown window).
     */
    public boolean canResend(String email, String purpose) {
        Optional<OtpCode> latestOpt = otpCodeRepository
                .findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(email, purpose);

        if (latestOpt.isEmpty()) {
            return true;
        }

        OtpCode otpCode = latestOpt.get();
        // If the latest unconsumed OTP is within cooldown, reject resend
        if (Instant.now().isBefore(otpCode.getCreatedAt().plus(RESEND_COOLDOWN))) {
            return false;
        }

        return true;
    }

    private String generateRawCode() {
        StringBuilder sb = new StringBuilder(OTP_LENGTH);
        for (int i = 0; i < OTP_LENGTH; i++) {
            sb.append(secureRandom.nextInt(10));
        }
        return sb.toString();
    }
}

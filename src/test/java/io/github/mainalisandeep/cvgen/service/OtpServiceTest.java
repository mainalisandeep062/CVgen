package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.entity.OtpCode;
import io.github.mainalisandeep.cvgen.repository.OtpCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OtpServiceTest {

    @Autowired
    private OtpService otpService;

    @Autowired
    private OtpCodeRepository otpCodeRepository;

    @BeforeEach
    void setUp() {
        otpCodeRepository.deleteAll();
    }

    @Test
    @DisplayName("AC4: OTP generation creates a hashed code, prior OTP invalidated")
    void generateInvalidatesPrior() {
        String otp1 = otpService.generate("user@test.com", OtpService.PURPOSE_SIGNUP);
        assertThat(otp1).hasSize(OtpService.OTP_LENGTH);

        // Generate second OTP — first should be invalidated
        String otp2 = otpService.generate("user@test.com", OtpService.PURPOSE_SIGNUP);
        assertThat(otp2).hasSize(OtpService.OTP_LENGTH);
        assertThat(otp2).isNotEqualTo(otp1);

        long unconsumedCount = otpCodeRepository.findAll().stream()
                .filter(o -> o.getConsumedAt() == null)
                .count();
        assertThat(unconsumedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("AC4: Correct OTP verifies successfully")
    void verifyCorrectOtp() {
        String rawOtp = otpService.generate("user@test.com", OtpService.PURPOSE_LOGIN);

        boolean result = otpService.verify("user@test.com", OtpService.PURPOSE_LOGIN, rawOtp);
        assertThat(result).isTrue();

        // Should be consumed after successful verification
        OtpCode code = otpCodeRepository.findAll().get(0);
        assertThat(code.getConsumedAt()).isNotNull();
    }

    @Test
    @DisplayName("AC5: 6 wrong attempts cap — 6th attempt rejected")
    void attemptCap() {
        String rawOtp = otpService.generate("user@test.com", OtpService.PURPOSE_LOGIN);

        // 5 wrong attempts
        for (int i = 0; i < 5; i++) {
            boolean result = otpService.verify("user@test.com", OtpService.PURPOSE_LOGIN, "000000");
            assertThat(result).isFalse();
        }

        // Even the correct code on 6th attempt should fail (cap reached)
        boolean sixthAttempt = otpService.verify("user@test.com", OtpService.PURPOSE_LOGIN, rawOtp);
        assertThat(sixthAttempt).isFalse();
    }

    @Test
    @DisplayName("AC5: Wrong OTP increments attempt count and returns false")
    void wrongOtpIncrementsAttempts() {
        otpService.generate("user@test.com", OtpService.PURPOSE_SIGNUP);

        boolean result = otpService.verify("user@test.com", OtpService.PURPOSE_SIGNUP, "999999");
        assertThat(result).isFalse();

        OtpCode code = otpCodeRepository.findAll().get(0);
        assertThat(code.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC6: Expired OTP is rejected even if code is correct")
    void expiredOtpRejected() throws InterruptedException {
        // Generate with 1 second expiry for testing
        String rawOtp = otpService.generate("user@test.com", OtpService.PURPOSE_LOGIN);

        // Wait for expiry (10 min is default, but we test the mechanism via DB manipulation)
        OtpCode code = otpCodeRepository.findAll().get(0);
        code.setExpiresAt(java.time.Instant.now().minusSeconds(1));
        otpCodeRepository.save(code);

        boolean result = otpService.verify("user@test.com", OtpService.PURPOSE_LOGIN, rawOtp);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Resend cooldown enforced")
    void resendCooldown() {
        otpService.generate("user@test.com", OtpService.PURPOSE_SIGNUP);

        // Immediately resend should be blocked
        boolean canResend = otpService.canResend("user@test.com", OtpService.PURPOSE_SIGNUP);
        assertThat(canResend).isFalse();
    }

    @Test
    @DisplayName("Different purpose OTPs are independent")
    void differentPurposesIndependent() {
        String signupOtp = otpService.generate("user@test.com", OtpService.PURPOSE_SIGNUP);
        String loginOtp = otpService.generate("user@test.com", OtpService.PURPOSE_LOGIN);

        assertThat(signupOtp).isNotEqualTo(loginOtp);

        // Verify signup OTP works for signup purpose
        assertThat(otpService.verify("user@test.com", OtpService.PURPOSE_SIGNUP, signupOtp)).isTrue();

        // But login OTP should still be valid for login
        assertThat(otpService.verify("user@test.com", OtpService.PURPOSE_LOGIN, loginOtp)).isTrue();
    }
}

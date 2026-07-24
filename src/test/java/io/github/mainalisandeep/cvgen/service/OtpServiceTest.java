package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.entity.OtpCode;
import io.github.mainalisandeep.cvgen.enums.OtpPurpose;
import io.github.mainalisandeep.cvgen.repository.OtpCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

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
        String otp1 = otpService.generate("user@test.com", OtpPurpose.SIGNUP);
        assertThat(otp1).hasSize(OtpService.OTP_LENGTH);

        String otp2 = otpService.generate("user@test.com", OtpPurpose.SIGNUP);
        assertThat(otp2).hasSize(OtpService.OTP_LENGTH);

        long unconsumedCount = otpCodeRepository.findAll().stream()
                .filter(otp -> otp.getConsumedAt() == null)
                .count();
        assertThat(unconsumedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("AC4: Correct OTP verifies successfully")
    void verifyCorrectOtp() {
        String rawOtp = otpService.generate("user@test.com", OtpPurpose.LOGIN);

        assertThat(otpService.verify("user@test.com", OtpPurpose.LOGIN, rawOtp)).isTrue();

        OtpCode code = otpCodeRepository.findAll().get(0);
        assertThat(code.getConsumedAt()).isNotNull();
    }

    @Test
    @DisplayName("AC5: attempt cap reached — even the correct code is rejected")
    void attemptCap() {
        String rawOtp = otpService.generate("user@test.com", OtpPurpose.LOGIN);

        for (int i = 0; i < OtpService.MAX_ATTEMPTS; i++) {
            assertThat(otpService.verify("user@test.com", OtpPurpose.LOGIN, "000000")).isFalse();
        }

        assertThat(otpService.verify("user@test.com", OtpPurpose.LOGIN, rawOtp)).isFalse();
    }

    @Test
    @DisplayName("AC5: Wrong OTP increments attempt count and returns false")
    void wrongOtpIncrementsAttempts() {
        otpService.generate("user@test.com", OtpPurpose.SIGNUP);

        assertThat(otpService.verify("user@test.com", OtpPurpose.SIGNUP, "999999")).isFalse();

        OtpCode code = otpCodeRepository.findAll().get(0);
        assertThat(code.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC6: Expired OTP is rejected even if code is correct")
    void expiredOtpRejected() {
        String rawOtp = otpService.generate("user@test.com", OtpPurpose.LOGIN);

        OtpCode code = otpCodeRepository.findAll().get(0);
        code.setExpiresAt(Instant.now().minusSeconds(1));
        otpCodeRepository.save(code);

        assertThat(otpService.verify("user@test.com", OtpPurpose.LOGIN, rawOtp)).isFalse();
    }

    @Test
    @DisplayName("Resend cooldown enforced while an OTP is pending")
    void resendCooldown() {
        otpService.generate("user@test.com", OtpPurpose.SIGNUP);

        assertThat(otpService.canResend("user@test.com", OtpPurpose.SIGNUP)).isFalse();
    }

    @Test
    @DisplayName("Resend allowed once the pending OTP is consumed")
    void resendAllowedWithoutPendingOtp() {
        String rawOtp = otpService.generate("user@test.com", OtpPurpose.SIGNUP);
        otpService.verify("user@test.com", OtpPurpose.SIGNUP, rawOtp);

        assertThat(otpService.canResend("user@test.com", OtpPurpose.SIGNUP)).isTrue();
    }

    @Test
    @DisplayName("Different purpose OTPs are independent")
    void differentPurposesIndependent() {
        String signupOtp = otpService.generate("user@test.com", OtpPurpose.SIGNUP);
        String loginOtp = otpService.generate("user@test.com", OtpPurpose.LOGIN);

        assertThat(otpService.verify("user@test.com", OtpPurpose.SIGNUP, signupOtp)).isTrue();
        assertThat(otpService.verify("user@test.com", OtpPurpose.LOGIN, loginOtp)).isTrue();
    }

    @Test
    @DisplayName("BaseEntity fills id and audit columns on persist")
    void auditColumnsPopulated() {
        otpService.generate("audit@test.com", OtpPurpose.SIGNUP);

        OtpCode code = otpCodeRepository.findAll().get(0);
        assertThat(code.getId()).isNotNull();
        assertThat(code.getCreatedAt()).isNotNull();
        assertThat(code.getUpdatedAt()).isNotNull();
    }
}

package io.github.mainalisandeep.cvgen.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mainalisandeep.cvgen.dto.LoginRequestDto;
import io.github.mainalisandeep.cvgen.dto.ResendOtpRequestDto;
import io.github.mainalisandeep.cvgen.dto.SignUpRequestDto;
import io.github.mainalisandeep.cvgen.dto.VerifyOtpRequestDto;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.enums.OtpPurpose;
import io.github.mainalisandeep.cvgen.repository.OtpCodeRepository;
import io.github.mainalisandeep.cvgen.repository.TrustedDeviceRepository;
import io.github.mainalisandeep.cvgen.repository.UserIdentityRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.security.util.CookieUtil;
import io.github.mainalisandeep.cvgen.service.OtpService;
import io.github.mainalisandeep.cvgen.service.TrustedDeviceService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class LocalAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpCodeRepository otpCodeRepository;

    @Autowired
    private TrustedDeviceRepository trustedDeviceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OtpService otpService;

    @Autowired
    private TrustedDeviceService trustedDeviceService;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        // identities first: they hold the FK to users
        userIdentityRepository.deleteAll();
        trustedDeviceRepository.deleteAll();
        otpCodeRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("AC4: Signup of a new user is accepted and sends an OTP")
    void signupNewUser() throws Exception {
        var request = new SignUpRequestDto("new@test.com", "password123", "New User");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Verification code sent to your email"))
                .andExpect(jsonPath("$.data.email").value("new@test.com"))
                .andExpect(jsonPath("$.data.purpose").value("SIGNUP"));

        User user = userRepository.findByEmail("new@test.com").orElseThrow();
        assertThat(user.getPasswordHash()).isNotBlank();
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(otpCodeRepository.findAll()).isNotEmpty();
    }

    @Test
    @DisplayName("AC8: Signup on an existing OAuth user attaches a password and keeps the verified email")
    void signupOnOAuthUser() throws Exception {
        userRepository.save(User.builder()
                .email("oauth@test.com")
                .name("OAuth User")
                .emailVerified(true)
                .build());

        var request = new SignUpRequestDto("oauth@test.com", "password123", "Updated Name");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value(true));

        User updated = userRepository.findByEmail("oauth@test.com").orElseThrow();
        assertThat(updated.getPasswordHash()).isNotBlank();
        assertThat(updated.getName()).isEqualTo("Updated Name");
        assertThat(updated.isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("Signup on an already-registered local user conflicts")
    void signupAlreadyRegistered() throws Exception {
        userRepository.save(User.builder()
                .email("exists@test.com")
                .name("Exists")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(true)
                .build());

        var request = new SignUpRequestDto("exists@test.com", "password123", "Exists");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));
    }

    @Test
    @DisplayName("Signup with an invalid payload reports field errors")
    void signupValidationFailure() throws Exception {
        var request = new SignUpRequestDto("not-an-email", "short", "");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    @DisplayName("Login with a trusted device skips OTP and returns an access token")
    void loginTrustedDeviceSkipsOtp() throws Exception {
        User user = userRepository.save(User.builder()
                .email("trusted@test.com")
                .name("Trusted")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(true)
                .build());

        String deviceToken = trustedDeviceService.remember(user);

        var request = new LoginRequestDto("trusted@test.com", "password123", true);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .cookie(new jakarta.servlet.http.Cookie(CookieUtil.TRUSTED_DEVICE_COOKIE, deviceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                // the refresh token only ever travels as an HttpOnly cookie
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(cookie().exists("cvgen_access_token"))
                .andExpect(cookie().httpOnly("cvgen_access_token", true));
    }

    @Test
    @DisplayName("Login without a trusted device requires OTP")
    void loginRequiresOtp() throws Exception {
        userRepository.save(User.builder()
                .email("otp@test.com")
                .name("OTP")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(true)
                .build());

        var request = new LoginRequestDto("otp@test.com", "password123", false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.purpose").value("LOGIN"));

        assertThat(otpCodeRepository.findAll()).isNotEmpty();
    }

    @Test
    @DisplayName("Login with a wrong password is unauthorized")
    void loginWrongPassword() throws Exception {
        userRepository.save(User.builder()
                .email("wrong@test.com")
                .name("Wrong")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(true)
                .build());

        var request = new LoginRequestDto("wrong@test.com", "wrongpass", false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("AC4+AC7: OTP verify marks the email verified and sets the remember-me cookie")
    void verifyOtpSignupWithRememberMe() throws Exception {
        userRepository.save(User.builder()
                .email("verify@test.com")
                .name("Verify")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(false)
                .build());

        String rawOtp = otpService.generate("verify@test.com", OtpPurpose.SIGNUP);

        var request = new VerifyOtpRequestDto("verify@test.com", OtpPurpose.SIGNUP, rawOtp, true);

        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(cookie().exists(CookieUtil.TRUSTED_DEVICE_COOKIE))
                .andExpect(cookie().httpOnly(CookieUtil.TRUSTED_DEVICE_COOKIE, true));

        assertThat(userRepository.findByEmail("verify@test.com").orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("OTP verify without remember-me sets no device cookie")
    void verifyOtpWithoutRememberMe() throws Exception {
        userRepository.save(User.builder()
                .email("noremember@test.com")
                .name("No Remember")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(false)
                .build());

        String rawOtp = otpService.generate("noremember@test.com", OtpPurpose.SIGNUP);

        var request = new VerifyOtpRequestDto("noremember@test.com", OtpPurpose.SIGNUP, rawOtp, false);

        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(CookieUtil.TRUSTED_DEVICE_COOKIE));

        assertThat(trustedDeviceRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("AC6: OTP verify with a wrong code is rejected")
    void verifyOtpWrongCode() throws Exception {
        userRepository.save(User.builder()
                .email("wrongotp@test.com")
                .name("Wrong OTP")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(false)
                .build());

        otpService.generate("wrongotp@test.com", OtpPurpose.SIGNUP);

        var request = new VerifyOtpRequestDto("wrongotp@test.com", OtpPurpose.SIGNUP, "000000", false);

        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.message").value("The verification code is invalid or has expired"));
    }

    @Test
    @DisplayName("OTP resend works once the cooldown has passed")
    void resendOtp() throws Exception {
        userRepository.save(User.builder()
                .email("resend@test.com")
                .name("Resend")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(false)
                .build());

        otpService.generate("resend@test.com", OtpPurpose.SIGNUP);
        backdateOtpCreation("resend@test.com", Instant.now().minusSeconds(120));

        var request = new ResendOtpRequestDto("resend@test.com", OtpPurpose.SIGNUP);

        mockMvc.perform(post("/api/auth/otp/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.purpose").value("SIGNUP"));
    }

    @Test
    @DisplayName("OTP resend inside the cooldown is rate limited")
    void resendOtpCooldown() throws Exception {
        userRepository.save(User.builder()
                .email("cooldown@test.com")
                .name("Cooldown")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(false)
                .build());

        otpService.generate("cooldown@test.com", OtpPurpose.SIGNUP);

        var request = new ResendOtpRequestDto("cooldown@test.com", OtpPurpose.SIGNUP);

        mockMvc.perform(post("/api/auth/otp/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Please wait before requesting another verification code"));
    }

    @Test
    @DisplayName("AC7: Login from another browser without the device cookie requires OTP")
    void loginDifferentBrowserRequiresOtp() throws Exception {
        User user = userRepository.save(User.builder()
                .email("different@test.com")
                .name("Different Browser")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(true)
                .build());

        // browser 1 is remembered, browser 2 sends no cookie
        trustedDeviceService.remember(user);

        var request = new LoginRequestDto("different@test.com", "password123", true);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.purpose").value("LOGIN"));
    }

    /** created_at is written by Hibernate and never updated, so ageing an OTP needs raw SQL. */
    private void backdateOtpCreation(String email, Instant createdAt) {
        entityManager.createNativeQuery("UPDATE otp_codes SET created_at = :createdAt WHERE email = :email")
                .setParameter("createdAt", createdAt)
                .setParameter("email", email)
                .executeUpdate();
        entityManager.clear();
    }
}

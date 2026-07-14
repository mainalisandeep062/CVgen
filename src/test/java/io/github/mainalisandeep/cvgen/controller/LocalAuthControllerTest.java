package io.github.mainalisandeep.cvgen.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.repository.OtpCodeRepository;
import io.github.mainalisandeep.cvgen.repository.TrustedDeviceRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.security.JwtTokenProvider;
import io.github.mainalisandeep.cvgen.service.OtpService;
import io.github.mainalisandeep.cvgen.service.TrustedDeviceService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;

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
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        trustedDeviceRepository.deleteAll();
        otpCodeRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("AC4: Signup new user returns OTP_SENT")
    void signupNewUser() throws Exception {
        var request = new LocalAuthController.SignupRequest("new@test.com", "password123", "New User");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("OTP_SENT"))
                .andExpect(jsonPath("$.email").value("new@test.com"));

        User user = userRepository.findByEmail("new@test.com").orElseThrow();
        assertThat(user.getPasswordHash()).isNotBlank();
        assertThat(user.isEmailVerified()).isFalse();

        // OTP should be generated
        assertThat(otpCodeRepository.findAll()).isNotEmpty();
    }

    @Test
    @DisplayName("AC8: Signup on existing OAuth user (no password) sets password and sends OTP")
    void signupOnOAuthUser() throws Exception {
        // Pre-existing OAuth user without password
        User oauthUser = User.builder()
                .email("oauth@test.com")
                .name("OAuth User")
                .emailVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userRepository.save(oauthUser);

        var request = new LocalAuthController.SignupRequest("oauth@test.com", "password123", "Updated Name");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("OTP_SENT"));

        User updated = userRepository.findByEmail("oauth@test.com").orElseThrow();
        assertThat(updated.getPasswordHash()).isNotBlank();
        assertThat(updated.getName()).isEqualTo("Updated Name");
        // emailVerified stays true (was already verified via OAuth)
        assertThat(updated.isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("Signup on already-registered local user returns ALREADY_REGISTERED")
    void signupAlreadyRegistered() throws Exception {
        User existing = User.builder()
                .email("exists@test.com")
                .name("Exists")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userRepository.save(existing);

        var request = new LocalAuthController.SignupRequest("exists@test.com", "password123", "Exists");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("Login with trusted device skips OTP and returns JWT")
    void loginTrustedDeviceSkipsOtp() throws Exception {
        // Create user with password
        User user = User.builder()
                .email("trusted@test.com")
                .name("Trusted")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userRepository.save(user);

        // Create trusted device
        String deviceToken = new TrustedDeviceService(trustedDeviceRepository, passwordEncoder).remember(user);

        var request = new LocalAuthController.LoginRequest("trusted@test.com", "password123", true);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .cookie(new jakarta.servlet.http.Cookie(TrustedDeviceService.COOKIE_NAME, deviceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    @DisplayName("Login without trusted device requires OTP")
    void loginRequiresOtp() throws Exception {
        User user = User.builder()
                .email("otp@test.com")
                .name("OTP")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userRepository.save(user);

        var request = new LocalAuthController.LoginRequest("otp@test.com", "password123", false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("OTP_REQUIRED"));

        assertThat(otpCodeRepository.findAll()).isNotEmpty();
    }

    @Test
    @DisplayName("Login with wrong password rejected")
    void loginWrongPassword() throws Exception {
        User user = User.builder()
                .email("wrong@test.com")
                .name("Wrong")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userRepository.save(user);

        var request = new LocalAuthController.LoginRequest("wrong@test.com", "wrongpass", false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("AC4+AC7: OTP verify with signup sets emailVerified=true and returns JWT with remember-me cookie")
    void verifyOtpSignupWithRememberMe() throws Exception {
        // Create user
        User user = User.builder()
                .email("verify@test.com")
                .name("Verify")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userRepository.save(user);

        // Generate OTP
        String rawOtp = new OtpService(otpCodeRepository, passwordEncoder)
                .generate("verify@test.com", OtpService.PURPOSE_SIGNUP);

        var request = new LocalAuthController.VerifyOtpRequest(
                "verify@test.com", OtpService.PURPOSE_SIGNUP, rawOtp, true);

        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(cookie().exists(TrustedDeviceService.COOKIE_NAME));

        User updated = userRepository.findByEmail("verify@test.com").orElseThrow();
        assertThat(updated.isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("AC6: OTP verify with wrong code returns INVALID_OTP")
    void verifyOtpWrongCode() throws Exception {
        User user = User.builder()
                .email("wrongotp@test.com")
                .name("Wrong OTP")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userRepository.save(user);

        new OtpService(otpCodeRepository, passwordEncoder)
                .generate("wrongotp@test.com", OtpService.PURPOSE_SIGNUP);

        var request = new LocalAuthController.VerifyOtpRequest(
                "wrongotp@test.com", OtpService.PURPOSE_SIGNUP, "000000", false);

        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("INVALID_OTP"));
    }

    @Test
    @DisplayName("OTP resend works after cooldown")
    void resendOtp() throws Exception {
        User user = User.builder()
                .email("resend@test.com")
                .name("Resend")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userRepository.save(user);

        // First generate
        new OtpService(otpCodeRepository, passwordEncoder)
                .generate("resend@test.com", OtpService.PURPOSE_SIGNUP);

        // Manually age the OTP to pass cooldown
        var otp = otpCodeRepository.findAll().get(0);
        otp.setCreatedAt(Instant.now().minusSeconds(120));
        otpCodeRepository.save(otp);

        var request = new LocalAuthController.ResendOtpRequest("resend@test.com", OtpService.PURPOSE_SIGNUP);

        mockMvc.perform(post("/api/auth/otp/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("OTP_SENT"));
    }

    @Test
    @DisplayName("OTP resend during cooldown is rejected")
    void resendOtpCooldown() throws Exception {
        User user = User.builder()
                .email("cooldown@test.com")
                .name("Cooldown")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userRepository.save(user);

        new OtpService(otpCodeRepository, passwordEncoder)
                .generate("cooldown@test.com", OtpService.PURPOSE_SIGNUP);

        var request = new LocalAuthController.ResendOtpRequest("cooldown@test.com", OtpService.PURPOSE_SIGNUP);

        mockMvc.perform(post("/api/auth/otp/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("RESEND_COOLDOWN"));
    }

    @Test
    @DisplayName("AC7: Login on different browser without device cookie requires OTP")
    void loginDifferentBrowserRequiresOtp() throws Exception {
        User user = User.builder()
                .email("different@test.com")
                .name("Different Browser")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userRepository.save(user);

        // Trusted device exists for browser 1
        new TrustedDeviceService(trustedDeviceRepository, passwordEncoder).remember(user);

        // But browser 2 doesn't send the cookie
        var request = new LocalAuthController.LoginRequest("different@test.com", "password123", true);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // No cookie sent, so OTP required
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("OTP_REQUIRED"));
    }
}

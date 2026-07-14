package io.github.mainalisandeep.cvgen.controller;

import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.security.JwtTokenProvider;
import io.github.mainalisandeep.cvgen.security.UserPrincipal;
import io.github.mainalisandeep.cvgen.service.MailService;
import io.github.mainalisandeep.cvgen.service.OtpService;
import io.github.mainalisandeep.cvgen.service.TrustedDeviceService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class LocalAuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final MailService mailService;
    private final TrustedDeviceService trustedDeviceService;
    private final JwtTokenProvider jwtTokenProvider;

    // ---- Signup ----

    @PostMapping("/signup")
    public ResponseEntity<OtpResponse> signup(@Valid @RequestBody SignupRequest request) {
        Optional<User> existingOpt = userRepository.findByEmail(request.email());

        if (existingOpt.isPresent()) {
            User existing = existingOpt.get();
            if (existing.getPasswordHash() != null && !existing.getPasswordHash().isBlank()) {
                // Already fully signed up locally
                return ResponseEntity.badRequest()
                        .body(new OtpResponse("ALREADY_REGISTERED", request.email(), null, null));
            }
            // OAuth-created user with no password — attach local login
            existing.setPasswordHash(passwordEncoder.encode(request.password()));
            existing.setName(request.name());
            existing.setUpdatedAt(Instant.now());
            userRepository.save(existing);
        } else {
            // New user
            User newUser = User.builder()
                    .email(request.email())
                    .name(request.name())
                    .passwordHash(passwordEncoder.encode(request.password()))
                    .emailVerified(false)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            userRepository.save(newUser);
        }

        String rawOtp = otpService.generate(request.email(), OtpService.PURPOSE_SIGNUP);
        mailService.sendOtpEmail(request.email(), rawOtp, (int) OtpService.OTP_EXPIRY.toMinutes(), OtpService.PURPOSE_SIGNUP);

        return ResponseEntity.accepted()
                .body(new OtpResponse("OTP_SENT", request.email(), null, null));
    }

    // ---- Login ----

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        User user = userRepository.findByEmail(request.email())
                .orElse(null);

        if (user == null || user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new OtpResponse("INVALID_CREDENTIALS", request.email(), null, null));
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            return ResponseEntity.badRequest()
                    .body(new OtpResponse("INVALID_CREDENTIALS", request.email(), null, null));
        }

        // Check trusted device
        String deviceCookie = extractCookie(httpRequest, TrustedDeviceService.COOKIE_NAME);
        boolean isTrusted = trustedDeviceService.isTrusted(user.getId(), deviceCookie);

        if (isTrusted) {
            // Skip OTP, issue JWT directly
            String accessToken = mintJwt(user);
            String refreshToken = jwtTokenProvider.generateRefreshToken(toPrincipal(user));
            return ResponseEntity.ok(new TokenResponse(accessToken, refreshToken));
        }

        // Not trusted — require OTP
        String rawOtp = otpService.generate(request.email(), OtpService.PURPOSE_LOGIN);
        mailService.sendOtpEmail(request.email(), rawOtp, (int) OtpService.OTP_EXPIRY.toMinutes(), OtpService.PURPOSE_LOGIN);

        return ResponseEntity.accepted()
                .body(new OtpResponse("OTP_REQUIRED", request.email(), null, null));
    }

    // ---- OTP Verify ----

    @PostMapping("/otp/verify")
    public ResponseEntity<?> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletResponse httpResponse
    ) {
        boolean valid = otpService.verify(request.email(), request.purpose(), request.code());

        if (!valid) {
            return ResponseEntity.badRequest()
                    .body(new OtpResponse("INVALID_OTP", request.email(), null, null));
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalStateException("User not found for verified OTP"));

        // If signup purpose, mark email as verified
        if (OtpService.PURPOSE_SIGNUP.equals(request.purpose())) {
            user.setEmailVerified(true);
            userRepository.save(user);
        }

        // Mint JWT directly (do NOT use OAuth2ExchangeCodeStore)
        String accessToken = mintJwt(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(toPrincipal(user));

        // Set trusted device cookie if requested
        if (Boolean.TRUE.equals(request.rememberMe())) {
            String rawDeviceToken = trustedDeviceService.remember(user);
            Cookie cookie = new Cookie(TrustedDeviceService.COOKIE_NAME, rawDeviceToken);
            cookie.setHttpOnly(true);
            cookie.setSecure(true);
            cookie.setPath("/");
            cookie.setMaxAge((int) TrustedDeviceService.REMEMBER_ME_DAYS.toSeconds());
            cookie.setAttribute("SameSite", "Lax");
            httpResponse.addCookie(cookie);
        }

        return ResponseEntity.ok(new TokenResponse(accessToken, refreshToken));
    }

    // ---- OTP Resend ----

    @PostMapping("/otp/resend")
    public ResponseEntity<OtpResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        if (!otpService.canResend(request.email(), request.purpose())) {
            return ResponseEntity.badRequest()
                    .body(new OtpResponse("RESEND_COOLDOWN", request.email(), null, null));
        }

        String rawOtp = otpService.generate(request.email(), request.purpose());
        mailService.sendOtpEmail(request.email(), rawOtp, (int) OtpService.OTP_EXPIRY.toMinutes(), request.purpose());

        return ResponseEntity.accepted()
                .body(new OtpResponse("OTP_SENT", request.email(), null, null));
    }

    // ---- Helpers ----

    private String mintJwt(User user) {
        UserPrincipal principal = toPrincipal(user);
        return jwtTokenProvider.generateToken(principal);
    }

    private UserPrincipal toPrincipal(User user) {
        Set<SimpleGrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        return UserPrincipal.localUser(
                user.getId().toString(),
                user.getName(),
                user.getEmail(),
                user.getEmail(),
                user.getPasswordHash(),
                authorities
        );
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookie.getName().equals(name)) {
                return cookie.getValue();
            }
        }
        return null;
    }

    // ---- Records ----

    public record SignupRequest(
            @NotBlank @Email String email,
            @NotBlank String password,
            @NotBlank String name
    ) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password,
            Boolean rememberMe
    ) {}

    public record VerifyOtpRequest(
            @NotBlank @Email String email,
            @NotBlank String purpose,
            @NotBlank String code,
            Boolean rememberMe
    ) {}

    public record ResendOtpRequest(
            @NotBlank @Email String email,
            @NotBlank String purpose
    ) {}

    public record OtpResponse(String status, String email, String accessToken, String refreshToken) {}

    public record TokenResponse(String accessToken, String refreshToken) {}
}

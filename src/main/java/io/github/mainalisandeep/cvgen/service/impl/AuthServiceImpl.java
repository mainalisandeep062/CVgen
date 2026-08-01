package io.github.mainalisandeep.cvgen.service.impl;

import io.github.mainalisandeep.cvgen.common.exception.BadRequestException;
import io.github.mainalisandeep.cvgen.common.exception.ConflictException;
import io.github.mainalisandeep.cvgen.common.exception.ResourceNotFoundException;
import io.github.mainalisandeep.cvgen.common.exception.TooManyRequestsException;
import io.github.mainalisandeep.cvgen.common.exception.UnauthorizedException;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.dto.LoginRequestDto;
import io.github.mainalisandeep.cvgen.dto.ResendOtpRequestDto;
import io.github.mainalisandeep.cvgen.dto.SignUpRequestDto;
import io.github.mainalisandeep.cvgen.dto.VerifyOtpRequestDto;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.enums.OtpPurpose;
import io.github.mainalisandeep.cvgen.mapper.UserMapper;
import io.github.mainalisandeep.cvgen.records.AuthTokens;
import io.github.mainalisandeep.cvgen.records.LoginResult;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.security.JwtTokenProvider;
import io.github.mainalisandeep.cvgen.security.UserPrincipal;
import io.github.mainalisandeep.cvgen.security.oauth2.OAuth2ExchangeCodeStore;
import io.github.mainalisandeep.cvgen.service.AuthService;
import io.github.mainalisandeep.cvgen.service.MailService;
import io.github.mainalisandeep.cvgen.service.OtpService;
import io.github.mainalisandeep.cvgen.service.TrustedDeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final MailService mailService;
    private final TrustedDeviceService trustedDeviceService;
    private final JwtTokenProvider jwtTokenProvider;
    private final OAuth2ExchangeCodeStore exchangeCodeStore;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public void signup(SignUpRequestDto request) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        if (user != null && user.hasLocalPassword()) {
            throw new ConflictException(ErrorConstantValue.EMAIL_ALREADY_REGISTERED);
        }

        if (user == null) {
            // brand new account
            user = User.builder()
                    .email(request.getEmail())
                    .emailVerified(false)
                    .build();
        }
        // an OAuth2-created account keeps its verified email and simply gains a password
        user.setName(request.getName());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        sendOtp(request.getEmail(), OtpPurpose.SIGNUP);
    }

    @Override
    public LoginResult login(LoginRequestDto request, String trustedDeviceToken) {
        User user = userRepository.findByEmail(request.getEmail())
                .filter(User::hasLocalPassword)
                .filter(candidate -> passwordEncoder.matches(request.getPassword(), candidate.getPasswordHash()))
                .orElseThrow(() -> new UnauthorizedException(ErrorConstantValue.INVALID_CREDENTIALS));

        if (trustedDeviceService.isTrusted(user.getId(), trustedDeviceToken)) {
            return LoginResult.authenticated(issueTokens(user, false));
        }

        sendOtp(user.getEmail(), OtpPurpose.LOGIN);
        return LoginResult.otpChallenge();
    }

    @Override
    @Transactional
    public AuthTokens verifyOtp(VerifyOtpRequestDto request) {
        if (!otpService.verify(request.getEmail(), request.getPurpose(), request.getCode())) {
            throw new BadRequestException(ErrorConstantValue.OTP_INVALID);
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.USER));

        if (OtpPurpose.SIGNUP == request.getPurpose() && !user.isEmailVerified()) {
            user.setEmailVerified(true);
            userRepository.save(user);
        }

        return issueTokens(user, Boolean.TRUE.equals(request.getRememberMe()));
    }

    @Override
    public void resendOtp(ResendOtpRequestDto request) {
        if (!otpService.canResend(request.getEmail(), request.getPurpose())) {
            throw new TooManyRequestsException(ErrorConstantValue.OTP_RESEND_COOLDOWN);
        }
        sendOtp(request.getEmail(), request.getPurpose());
    }

    @Override
    public AuthTokens exchangeOAuth2Code(String code) {
        UUID userId = exchangeCodeStore.consumeExchangeCode(code)
                .orElseThrow(() -> new BadRequestException(ErrorConstantValue.EXCHANGE_CODE_INVALID));
        return issueTokens(findUser(userId), false);
    }

    @Override
    public AuthTokens refresh(String refreshToken) {
        if (refreshToken == null || !jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new UnauthorizedException(ErrorConstantValue.REFRESH_TOKEN_INVALID);
        }
        return issueTokens(findUser(jwtTokenProvider.getUserIdFromToken(refreshToken)), false);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.USER));
    }

    private AuthTokens issueTokens(User user, boolean rememberDevice) {
        UserPrincipal principal = userMapper.toPrincipal(user);
        return new AuthTokens(
                jwtTokenProvider.generateToken(principal),
                jwtTokenProvider.generateRefreshToken(principal),
                rememberDevice ? trustedDeviceService.remember(user) : null
        );
    }

    private void sendOtp(String email, OtpPurpose purpose) {
        String rawOtp = otpService.generate(email, purpose);
        mailService.sendOtpEmail(email, rawOtp, (int) OtpService.OTP_EXPIRY.toMinutes(), purpose);
    }
}

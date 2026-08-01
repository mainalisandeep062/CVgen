package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.dto.LoginRequestDto;
import io.github.mainalisandeep.cvgen.dto.ResendOtpRequestDto;
import io.github.mainalisandeep.cvgen.dto.SignUpRequestDto;
import io.github.mainalisandeep.cvgen.dto.VerifyOtpRequestDto;
import io.github.mainalisandeep.cvgen.records.AuthTokens;
import io.github.mainalisandeep.cvgen.records.LoginResult;

/**
 * Authentication use cases. Every failure is signalled with a
 * {@code common.exception.BaseException}; controllers only shape the HTTP response.
 */
public interface AuthService {

    /** Registers (or completes) a local account and emails a signup OTP. */
    void signup(SignUpRequestDto request);

    /** Verifies the password, then either issues tokens (trusted device) or an OTP challenge. */
    LoginResult login(LoginRequestDto request, String trustedDeviceToken);

    /** Consumes an OTP and issues tokens. */
    AuthTokens verifyOtp(VerifyOtpRequestDto request);

    /** Re-sends an OTP once the cooldown has passed. */
    void resendOtp(ResendOtpRequestDto request);

    /** Trades a one-time OAuth2 exchange code for tokens. */
    AuthTokens exchangeOAuth2Code(String code);

    /** Rotates a refresh token. */
    AuthTokens refresh(String refreshToken);
}

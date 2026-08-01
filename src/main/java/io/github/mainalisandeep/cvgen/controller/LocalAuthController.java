package io.github.mainalisandeep.cvgen.controller;

import io.github.mainalisandeep.cvgen.common.controller.BaseController;
import io.github.mainalisandeep.cvgen.common.message.SuccessResponseConstant;
import io.github.mainalisandeep.cvgen.common.response.GlobalApiResponse;
import io.github.mainalisandeep.cvgen.dto.LoginRequestDto;
import io.github.mainalisandeep.cvgen.dto.ResendOtpRequestDto;
import io.github.mainalisandeep.cvgen.dto.SignUpRequestDto;
import io.github.mainalisandeep.cvgen.dto.VerifyOtpRequestDto;
import io.github.mainalisandeep.cvgen.enums.OtpPurpose;
import io.github.mainalisandeep.cvgen.records.AuthTokens;
import io.github.mainalisandeep.cvgen.records.LoginResult;
import io.github.mainalisandeep.cvgen.records.OtpResponse;
import io.github.mainalisandeep.cvgen.records.TokenResponse;
import io.github.mainalisandeep.cvgen.security.util.CookieUtil;
import io.github.mainalisandeep.cvgen.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Email + password authentication. Business rules live in {@link AuthService};
 * this layer only maps results to HTTP responses and cookies.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class LocalAuthController extends BaseController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;

    @PostMapping("/signup")
    public ResponseEntity<GlobalApiResponse<OtpResponse>> signup(@Valid @RequestBody SignUpRequestDto request) {
        authService.signup(request);
        return respond(HttpStatus.ACCEPTED, SuccessResponseConstant.OTP_SENT,
                new OtpResponse(request.getEmail(), OtpPurpose.SIGNUP));
    }


    @PostMapping("/login")
    public ResponseEntity<GlobalApiResponse<?>> login(
            @Valid @RequestBody LoginRequestDto request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        LoginResult result = authService.login(request, cookieUtil.extractTrustedDeviceToken(httpRequest));

        if (result.otpRequired()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                    successResponse(customMessageSource.get(SuccessResponseConstant.OTP_REQUIRED),
                            new OtpResponse(request.getEmail(), OtpPurpose.LOGIN)));
        }

        writeAuthCookies(result.tokens(), httpRequest, httpResponse);
        return ResponseEntity.ok(successResponse(customMessageSource.get(SuccessResponseConstant.LOGIN_SUCCESS),
                new TokenResponse(result.tokens().accessToken())));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<GlobalApiResponse<TokenResponse>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequestDto request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        AuthTokens tokens = authService.verifyOtp(request);
        writeAuthCookies(tokens, httpRequest, httpResponse);
        return ok(SuccessResponseConstant.OTP_VERIFIED, new TokenResponse(tokens.accessToken()));
    }

    @PostMapping("/otp/resend")
    public ResponseEntity<GlobalApiResponse<OtpResponse>> resendOtp(@Valid @RequestBody ResendOtpRequestDto request) {
        authService.resendOtp(request);
        return respond(HttpStatus.ACCEPTED, SuccessResponseConstant.OTP_SENT,
                new OtpResponse(request.getEmail(), request.getPurpose()));
    }

    /** Refresh token always, remember-me device token only when the user asked for it. */
    private void writeAuthCookies(AuthTokens tokens, HttpServletRequest request, HttpServletResponse response) {
        boolean secure = request.isSecure();
        response.addHeader(HttpHeaders.SET_COOKIE, cookieUtil.buildRefreshCookie(tokens.refreshToken(), secure).toString());
        if (tokens.hasTrustedDeviceToken()) {
            response.addHeader(HttpHeaders.SET_COOKIE,
                    cookieUtil.buildTrustedDeviceCookie(tokens.trustedDeviceToken(), secure).toString());
        }
    }
}

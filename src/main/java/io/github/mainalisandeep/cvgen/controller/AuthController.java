package io.github.mainalisandeep.cvgen.controller;

import io.github.mainalisandeep.cvgen.common.controller.BaseController;
import io.github.mainalisandeep.cvgen.common.message.SuccessResponseConstant;
import io.github.mainalisandeep.cvgen.common.response.GlobalApiResponse;
import io.github.mainalisandeep.cvgen.records.AuthTokens;
import io.github.mainalisandeep.cvgen.records.ExchangeCodeRequest;
import io.github.mainalisandeep.cvgen.records.TokenResponse;
import io.github.mainalisandeep.cvgen.security.util.CookieUtil;
import io.github.mainalisandeep.cvgen.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token endpoints shared by the OAuth2 and local flows.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController extends BaseController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;

    @PostMapping("/oauth/exchange")
    public ResponseEntity<GlobalApiResponse<TokenResponse>> exchangeCode(
            @Valid @RequestBody ExchangeCodeRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        AuthTokens tokens = authService.exchangeOAuth2Code(request.code());
        writeRefreshCookie(tokens, httpRequest, httpResponse);
        return ok(SuccessResponseConstant.LOGIN_SUCCESS, new TokenResponse(tokens.accessToken()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<GlobalApiResponse<TokenResponse>> refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AuthTokens tokens = authService.refresh(cookieUtil.extractRefreshToken(httpRequest));
        writeRefreshCookie(tokens, httpRequest, httpResponse);
        return ok(SuccessResponseConstant.TOKEN_REFRESHED, new TokenResponse(tokens.accessToken()));
    }

    /**
     * Ends the session: expires the refresh cookie and kills any server-side HTTP session
     * (an OAuth2 login leaves one behind). The trusted-device cookie deliberately survives —
     * "remember this device" means the OTP stays skipped for its full 30 days, across logouts.
     */
    @PostMapping("/logout")
    public ResponseEntity<GlobalApiResponse<Object>> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookieUtil.clearRefreshCookie(httpRequest.isSecure()).toString());
        return ok(SuccessResponseConstant.LOGOUT_SUCCESS, null);
    }

    /** Refresh tokens are rotated on every use and only ever travel in an HttpOnly cookie. */
    private void writeRefreshCookie(AuthTokens tokens, HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieUtil.buildRefreshCookie(tokens.refreshToken(), request.isSecure()).toString());
    }
}

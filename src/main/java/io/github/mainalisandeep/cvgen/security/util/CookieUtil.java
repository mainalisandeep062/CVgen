package io.github.mainalisandeep.cvgen.security.util;

import io.github.mainalisandeep.cvgen.config.SecurityProperties;
import io.github.mainalisandeep.cvgen.security.JwtTokenProvider;
import io.github.mainalisandeep.cvgen.service.TrustedDeviceService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds and reads the auth cookies. Every cookie the application sets is defined here,
 * so flags (HttpOnly, Secure, SameSite, Path, Max-Age) stay consistent across endpoints.
 */
@Component
public class CookieUtil {

    /** Remember-this-device cookie; sent on every path because login lives outside /api/auth too. */
    public static final String TRUSTED_DEVICE_COOKIE = "cvgen_remember_device";

    private static final String REFRESH_COOKIE_PATH = "/api/auth";
    private static final String ROOT_PATH = "/";

    private final SecurityProperties.OAuth2 props;

    public CookieUtil(SecurityProperties securityProperties) {
        this.props = securityProperties.getOauth2();
    }

    public ResponseCookie buildRefreshCookie(String refreshToken, boolean secureRequest) {
        return refreshCookie(refreshToken, secureRequest, JwtTokenProvider.REFRESH_TOKEN_TTL);
    }

    public ResponseCookie clearRefreshCookie(boolean secureRequest) {
        return refreshCookie("", secureRequest, Duration.ZERO);
    }

    public ResponseCookie buildTrustedDeviceCookie(String rawToken, boolean secureRequest) {
        return trustedDeviceCookie(rawToken, secureRequest, TrustedDeviceService.REMEMBER_ME_DURATION);
    }

    public ResponseCookie clearTrustedDeviceCookie(boolean secureRequest) {
        return trustedDeviceCookie("", secureRequest, Duration.ZERO);
    }

    public String extractRefreshToken(HttpServletRequest request) {
        return extract(request, props.getAccessTokenCookieName());
    }

    public String extractTrustedDeviceToken(HttpServletRequest request) {
        return extract(request, TRUSTED_DEVICE_COOKIE);
    }

    public String extract(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || name == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie refreshCookie(String value, boolean secureRequest, Duration maxAge) {
        return ResponseCookie.from(props.getAccessTokenCookieName(), value)
                .httpOnly(true)
                .secure(secureRequest)
                .sameSite(sameSite(secureRequest))
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie trustedDeviceCookie(String value, boolean secureRequest, Duration maxAge) {
        return ResponseCookie.from(TRUSTED_DEVICE_COOKIE, value)
                .httpOnly(true)
                .secure(secureRequest)
                .sameSite(sameSite(secureRequest))
                .path(ROOT_PATH)
                .maxAge(maxAge)
                .build();
    }

    private String sameSite(boolean secureRequest) {
        return secureRequest ? props.getSecureSameSite() : props.getInsecureSameSite();
    }
}

package io.github.mainalisandeep.cvgen.security.util;

import io.github.mainalisandeep.cvgen.config.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CookieUtil {

    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    private final SecurityProperties.OAuth2 props;

    public CookieUtil(SecurityProperties securityProperties) {
        this.props = securityProperties.getOauth2();
    }

    public ResponseCookie buildRefreshCookie(String refreshToken, boolean secureRequest) {
        String sameSite = secureRequest ? props.getSecureSameSite() : props.getInsecureSameSite();
        return ResponseCookie.from(props.getAccessTokenCookieName(), refreshToken)
                .httpOnly(true)
                .secure(secureRequest)
                .sameSite(sameSite)
                .path("/api/auth")
                .maxAge(REFRESH_TOKEN_TTL)
                .build();
    }

    public ResponseCookie clearRefreshCookie(boolean secureRequest) {
        String sameSite = secureRequest ? props.getSecureSameSite() : props.getInsecureSameSite();
        return ResponseCookie.from(props.getAccessTokenCookieName(), "")
                .httpOnly(true)
                .secure(secureRequest)
                .sameSite(sameSite)
                .path("/api/auth")
                .maxAge(0)
                .build();
    }

    public String extractRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (jakarta.servlet.http.Cookie c : request.getCookies()) {
            if (props.getAccessTokenCookieName().equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
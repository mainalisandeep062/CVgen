package io.github.mainalisandeep.cvgen.config.constants;

import java.util.List;

/**
 * Request paths that bypass authentication.
 */
public final class MatchersConfig {

    private MatchersConfig() {
    }

    /** Signup, login, OTP, token refresh and logout all live under /api/auth. */
    public static final List<String> PUBLIC_MATCHERS = List.of(
            "/api/auth/**",
            "/api/webhook/**",
            "/public/**",
            "/websocket/**",
            "/oauth2/logout"
    );

    public static final List<String> SWAGGER_MATCHERS = List.of(
            "/",
            "/error",
            "/favicon.ico",
            "/assets/**",
            "/css/**",
            "/js/**",
            "/images/**",
            "/webjars/**",
            "/oauth2/**",
            "/login/oauth2/**",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    );
}

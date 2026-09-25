package io.github.mainalisandeep.cvgen.config.constants;

import java.util.List;

/**
 * Request paths with an authorization rule of their own: public ones bypass authentication, admin
 * ones require the ADMIN role.
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
            "/oauth2/logout",
            // Avatars render from <img src>, which cannot send a bearer token. Scoped to this one
            // path on purpose: it must not widen to /api/files/**, where CV PDFs will live.
            "/api/files/avatars/*"
    );

    /**
     * Admin API. Guarded twice: by role here, from the token, and by {@code AdminAccessGuard} on every
     * admin controller, from the database, because a token outlives a demotion.
     */
    public static final List<String> ADMIN_MATCHERS = List.of(
            "/api/admin/**"
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

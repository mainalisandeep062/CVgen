package io.github.mainalisandeep.cvgen.config.constants;

import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
public final class MatchersConfig {

    public static final List<String> PUBLIC_MATCHERS = List.of(
            "/api/email/**",
            "/api/sign-up/**",
            "/api/auth/**",
            "/api/otp/**",
            "/api/otp/verify-otp/**",
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
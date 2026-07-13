package io.github.mainalisandeep.cvgen.config.constants;

import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
public final class MatchersConfig {

    public static final List<String> PUBLIC_MATCHERS = List.of(
            "/api/v1/email/**",
            "/api/v1/sign-up/**",
            "/api/v1/auth/**",
            "/api/v1/otp/**",
            "/api/v1/otp/verify-otp/**",
            "/api/v1/webhook/**",
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
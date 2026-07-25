package io.github.mainalisandeep.cvgen.security;

import io.github.mainalisandeep.cvgen.config.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final String bearerPrefix;

    public JwtAuthFilter(JwtTokenProvider jwtTokenProvider, SecurityProperties securityProperties) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.bearerPrefix = securityProperties.getOauth2().getBearerPrefix().trim() + " ";
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        // A valid Bearer token always wins, even over an authentication restored from the
        // HTTP session (e.g. a lingering OAuth2 login) — the API is token-driven.
        if (token != null && jwtTokenProvider.validateAccessToken(token)) {
            SecurityContextHolder.getContext().setAuthentication(jwtTokenProvider.getAuthentication(token));
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader != null && authorizationHeader.startsWith(bearerPrefix)) {
            return authorizationHeader.substring(bearerPrefix.length()).trim();
        }
        return null;
    }
}

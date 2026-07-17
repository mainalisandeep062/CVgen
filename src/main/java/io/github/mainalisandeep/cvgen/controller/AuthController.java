package io.github.mainalisandeep.cvgen.controller;

import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.records.ExchangeCodeRequest;
import io.github.mainalisandeep.cvgen.records.TokenResponse;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.security.JwtTokenProvider;
import io.github.mainalisandeep.cvgen.security.UserPrincipal;
import io.github.mainalisandeep.cvgen.security.oauth2.OAuth2ExchangeCodeStore;
import io.github.mainalisandeep.cvgen.security.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final OAuth2ExchangeCodeStore exchangeCodeStore;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final CookieUtil cookieUtil;

    @PostMapping("/oauth/exchange")
    public ResponseEntity<TokenResponse> exchangeCode(
            @Valid @RequestBody ExchangeCodeRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        UUID userId = exchangeCodeStore.consumeExchangeCode(request.code())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired exchange code"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found for exchange code"));

        UserPrincipal principal = toPrincipal(user);

        String accessToken = jwtTokenProvider.generateToken(principal);
        String refreshToken = jwtTokenProvider.generateRefreshToken(principal);

        httpResponse.addHeader(HttpHeaders.SET_COOKIE,
                cookieUtil.buildRefreshCookie(refreshToken, httpRequest.isSecure()).toString());

        return ResponseEntity.ok(new TokenResponse(accessToken));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String refreshToken = cookieUtil.extractRefreshToken(httpRequest);
        if (refreshToken == null || !jwtTokenProvider.validateRefreshToken(refreshToken)) {
            return ResponseEntity.status(401).build();
        }

        String userId = jwtTokenProvider.getClaims(refreshToken).get("id", String.class);
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new IllegalStateException("User not found for refresh token"));

        UserPrincipal principal = toPrincipal(user);
        String newAccessToken = jwtTokenProvider.generateToken(principal);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(principal); // rotation

        httpResponse.addHeader(HttpHeaders.SET_COOKIE,
                cookieUtil.buildRefreshCookie(newRefreshToken, httpRequest.isSecure()).toString());

        return ResponseEntity.ok(new TokenResponse(newAccessToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        httpResponse.addHeader(HttpHeaders.SET_COOKIE,
                cookieUtil.clearRefreshCookie(httpRequest.isSecure()).toString());
        return ResponseEntity.noContent().build();
    }

    private UserPrincipal toPrincipal(User user) {
        Set<SimpleGrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        return UserPrincipal.localUser(
                user.getId().toString(), user.getName(), user.getEmail(),
                user.getEmail(), user.getPasswordHash(), authorities);
    }
}


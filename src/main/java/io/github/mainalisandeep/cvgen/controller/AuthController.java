package io.github.mainalisandeep.cvgen.controller;

import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.security.JwtTokenProvider;
import io.github.mainalisandeep.cvgen.security.UserPrincipal;
import io.github.mainalisandeep.cvgen.security.oauth2.OAuth2ExchangeCodeStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
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

    @PostMapping("/oauth/exchange")
    public ResponseEntity<TokenResponse> exchangeCode(@Valid @RequestBody ExchangeCodeRequest request) {
        UUID userId = exchangeCodeStore.consumeExchangeCode(request.code())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired exchange code"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found for exchange code"));

        Set<SimpleGrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        UserPrincipal principal = UserPrincipal.localUser(
                user.getId().toString(),
                user.getName(),
                user.getEmail(),
                user.getEmail(),
                user.getPasswordHash(),
                authorities
        );

        String accessToken = jwtTokenProvider.generateToken(principal);
        String refreshToken = jwtTokenProvider.generateRefreshToken(principal);

        return ResponseEntity.ok(new TokenResponse(accessToken, refreshToken));
    }

    public record ExchangeCodeRequest(@NotBlank String code) {
    }

    public record TokenResponse(String accessToken, String refreshToken) {
    }
}

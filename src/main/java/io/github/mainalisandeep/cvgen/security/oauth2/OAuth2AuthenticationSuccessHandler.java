package io.github.mainalisandeep.cvgen.security.oauth2;

import io.github.mainalisandeep.cvgen.config.SecurityProperties;
import io.github.mainalisandeep.cvgen.security.IdentifiedPrincipal;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Duration EXCHANGE_CODE_TTL = Duration.ofSeconds(60);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecurityProperties securityProperties;
    private final OAuth2ExchangeCodeStore exchangeCodeStore;

    public OAuth2AuthenticationSuccessHandler(
            SecurityProperties securityProperties,
            OAuth2ExchangeCodeStore exchangeCodeStore
    ) {
        this.securityProperties = securityProperties;
        this.exchangeCodeStore = exchangeCodeStore;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        IdentifiedPrincipal principal = extractPrincipal(authentication);

        String exchangeCode = generateExchangeCode();
        exchangeCodeStore.storeExchangeCode(exchangeCode, UUID.fromString(principal.getId()), EXCHANGE_CODE_TTL);

        String redirectUri = UriComponentsBuilder.fromUriString(resolveRedirectUri())
                .queryParam("code", exchangeCode)
                .build()
                .toUriString();

        response.sendRedirect(redirectUri);
    }

    private IdentifiedPrincipal extractPrincipal(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof IdentifiedPrincipal identifiedPrincipal) {
            return identifiedPrincipal;
        }

        throw new IllegalStateException("OAuth2 authentication did not produce an `IdentifiedPrincipal` principal");
    }

    private String generateExchangeCode() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String resolveRedirectUri() {
        List<String> authorizedUris = securityProperties.getOauth2().getAuthorizedRedirectUris();
        String configured = securityProperties.getOauth2().getSuccessRedirectUri();
        if (StringUtils.hasText(configured) && authorizedUris.contains(configured)) {
            return configured;
        }
        if (!authorizedUris.isEmpty()) {
            return authorizedUris.get(0);
        }
        return configured;
    }
}

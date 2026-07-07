package io.github.mainalisandeep.cvgen.security.oauth2;

import io.github.mainalisandeep.cvgen.config.SecurityProperties;
import io.github.mainalisandeep.cvgen.security.JwtTokenProvider;
import io.github.mainalisandeep.cvgen.security.UserPrinciple;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

	private static final String ACCESS_TOKEN_COOKIE = "cvgen_access_token";

	private final JwtTokenProvider jwtTokenProvider;
	private final SecurityProperties securityProperties;
	private final OAuth2ExchangeCodeStore exchangeCodeStore;

	public OAuth2AuthenticationSuccessHandler(
			JwtTokenProvider jwtTokenProvider,
			SecurityProperties securityProperties,
			OAuth2ExchangeCodeStore exchangeCodeStore
	) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.securityProperties = securityProperties;
		this.exchangeCodeStore = exchangeCodeStore;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
		UserPrinciple principal = extractPrincipal(authentication);
		String jwt = jwtTokenProvider.generateToken(principal);

		Duration expiration = securityProperties.getJwt().getExpiration();
		exchangeCodeStore.storeIssuedToken(principal.getUsername(), jwt, expiration);

		ResponseCookie cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, jwt)
				.httpOnly(true)
				.secure(request.isSecure())
				.sameSite(request.isSecure() ? "None" : "Lax")
				.path("/")
				.maxAge(expiration)
				.build();

		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
		response.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
		SecurityContextHolder.getContext().setAuthentication(authentication);

		String redirectUri = resolveRedirectUri();
		response.sendRedirect(redirectUri);
	}

	private UserPrinciple extractPrincipal(Authentication authentication) {
		Object principal = authentication.getPrincipal();
		if (principal instanceof UserPrinciple userPrinciple) {
			return userPrinciple;
		}

		throw new IllegalStateException("OAuth2 authentication did not produce a `UserPrinciple` principal");
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

package io.github.mainalisandeep.cvgen.security.oauth2;

import io.github.mainalisandeep.cvgen.config.SecurityProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

	private final SecurityProperties securityProperties;

	public OAuth2AuthenticationFailureHandler(SecurityProperties securityProperties) {
		this.securityProperties = securityProperties;
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
		String redirectUri = UriComponentsBuilder.fromUriString(securityProperties.getOauth2().getFailureRedirectUri())
				.queryParam("error", exception.getMessage() == null ? "oauth2_authentication_failed" : exception.getMessage())
				.build()
				.toUriString();
		response.sendRedirect(redirectUri);
	}
}

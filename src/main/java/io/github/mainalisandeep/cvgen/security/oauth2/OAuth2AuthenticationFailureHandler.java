package io.github.mainalisandeep.cvgen.security.oauth2;

import io.github.mainalisandeep.cvgen.config.SecurityProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

	private final SecurityProperties securityProperties;
	private final String failureErrorCode;

	public OAuth2AuthenticationFailureHandler(SecurityProperties securityProperties, @Value("${app.security.oauth2.failure-error-code}") String failureErrorCode) {
		this.securityProperties = securityProperties;
		this.failureErrorCode = failureErrorCode;
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
		String redirectUri = UriComponentsBuilder.fromUriString(securityProperties.getOauth2().getFailureRedirectUri())
				.queryParam("error", exception.getMessage() == null ? failureErrorCode : exception.getMessage())
				.build()
				.toUriString();
		response.sendRedirect(redirectUri);
	}
}

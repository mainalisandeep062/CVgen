package io.github.mainalisandeep.cvgen.security.oauth2;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.util.Map;

public final class OAuth2UserInfoFactory {

	private OAuth2UserInfoFactory() {
	}

	public static OAuth2UserInfo getUserInfo(String registrationId, Map<String, Object> attributes) {
		if (registrationId == null) {
			throw unsupportedProvider("null");
		}

		return switch (registrationId.toLowerCase()) {
			case "google" -> new GoogleOAuth2UserInfo(attributes);
			case "github" -> new GithubOAuth2UserInfo(attributes);
			case "linkedin" -> new LinkedinOAuth2UserInfo(attributes);
			default -> throw unsupportedProvider(registrationId);
		};
	}

	private static OAuth2AuthenticationException unsupportedProvider(String registrationId) {
		return new OAuth2AuthenticationException(
				new OAuth2Error("unsupported_oauth2_provider"),
				"Unsupported OAuth2 provider: " + registrationId
		);
	}
}

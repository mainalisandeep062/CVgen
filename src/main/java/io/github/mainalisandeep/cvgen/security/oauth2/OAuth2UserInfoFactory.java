package io.github.mainalisandeep.cvgen.security.oauth2;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class OAuth2UserInfoFactory {
	private final String googleProviderId;
	private final String githubProviderId;
	private final String linkedinProviderId;
	private final String unsupportedProviderErrorCode;

	public OAuth2UserInfoFactory(
			@Value("${app.security.oauth2.google-provider-id}") String googleProviderId,
			@Value("${app.security.oauth2.github-provider-id}") String githubProviderId,
			@Value("${app.security.oauth2.linkedin-provider-id}") String linkedinProviderId,
			@Value("${app.security.oauth2.unsupported-provider-error-code}") String unsupportedProviderErrorCode
	) {
		this.googleProviderId = googleProviderId;
		this.githubProviderId = githubProviderId;
		this.linkedinProviderId = linkedinProviderId;
		this.unsupportedProviderErrorCode = unsupportedProviderErrorCode;
	}

	public OAuth2UserInfo getUserInfo(String registrationId, Map<String, Object> attributes) {
		if (registrationId == null) {
			throw unsupportedProvider("null");
		}

		String normalized = registrationId.trim();
		if (normalized.equalsIgnoreCase(googleProviderId)) {
			return new GoogleOAuth2UserInfo(attributes);
		}
		if (normalized.equalsIgnoreCase(githubProviderId)) {
			return new GithubOAuth2UserInfo(attributes);
		}
		if (normalized.equalsIgnoreCase(linkedinProviderId)) {
			return new LinkedinOAuth2UserInfo(attributes);
		}
		throw unsupportedProvider(registrationId);
	}

	private OAuth2AuthenticationException unsupportedProvider(String registrationId) {
		return new OAuth2AuthenticationException(
				new OAuth2Error(unsupportedProviderErrorCode),
				"Unsupported OAuth2 provider: " + registrationId
		);
	}
}

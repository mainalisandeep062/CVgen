package io.github.mainalisandeep.cvgen.security.oauth2;

import java.util.Map;

public class GithubOAuth2UserInfo extends OAuth2UserInfo {

	public GithubOAuth2UserInfo(Map<String, Object> attributes) {
		super(attributes);
	}

	@Override
	public String getId() {
		return firstNonBlank(getString("id"), getString("node_id"));
	}

	@Override
	public String getName() {
		return firstNonBlank(getString("name"), getString("login"), getEmail(), getId());
	}

	@Override
	public String getEmail() {
		return firstNonBlank(getString("email"), getString("login") == null ? null : getString("login") + "@users.noreply.github.com");
	}

	@Override
	public String getImageUrl() {
		return getString("avatar_url");
	}

	@Override
	public boolean isEmailVerified() {
		Object verified = attributes.get("email_verified");
		if (verified instanceof Boolean b) {
			return b;
		}
		if (verified instanceof String s) {
			return Boolean.parseBoolean(s);
		}
		return false;
	}

	private String getString(String key) {
		Object value = attributes.get(key);
		return value == null ? null : String.valueOf(value);
	}

	private String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}
}

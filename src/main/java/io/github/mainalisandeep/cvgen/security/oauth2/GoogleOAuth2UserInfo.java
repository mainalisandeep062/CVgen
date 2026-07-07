package io.github.mainalisandeep.cvgen.security.oauth2;

import java.util.Map;

public class GoogleOAuth2UserInfo extends OAuth2UserInfo {

	public GoogleOAuth2UserInfo(Map<String, Object> attributes) {
		super(attributes);
	}

	@Override
	public String getId() {
		return getString("sub");
	}

	@Override
	public String getName() {
		return firstNonBlank(getString("name"), getString("given_name"), getString("email"));
	}

	@Override
	public String getEmail() {
		return getString("email");
	}

	@Override
	public String getImageUrl() {
		return getString("picture");
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

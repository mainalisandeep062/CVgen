package io.github.mainalisandeep.cvgen.security.oauth2;

import java.util.Map;

public class LinkedinOAuth2UserInfo extends OAuth2UserInfo {

	public LinkedinOAuth2UserInfo(Map<String, Object> attributes) {
		super(attributes);
	}

	@Override
	public String getId() {
		return firstNonBlank(getString("sub"), getString("id"), getString("user_id"));
	}

	@Override
	public String getName() {
		return firstNonBlank(
				getString("name"),
				concatenate(getString("localizedFirstName"), getString("localizedLastName")),
				getString("given_name"),
				getString("email"),
				getId()
		);
	}

	@Override
	public String getEmail() {
		return firstNonBlank(getString("email"), getString("emailAddress"));
	}

	@Override
	public String getImageUrl() {
		Object picture = attributes.get("picture");
		if (picture instanceof Map<?, ?> pictureMap) {
			Object data = pictureMap.get("data");
			if (data instanceof Map<?, ?> dataMap) {
				Object url = dataMap.get("url");
				if (url != null) {
					return String.valueOf(url);
				}
			}
		}

		return getString("profilePicture");
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

	private String concatenate(String first, String last) {
		if ((first == null || first.isBlank()) && (last == null || last.isBlank())) {
			return null;
		}
		if (first == null || first.isBlank()) {
			return last;
		}
		if (last == null || last.isBlank()) {
			return first;
		}
		return first + " " + last;
	}
}

package io.github.mainalisandeep.cvgen.security.oauth2;

import io.github.mainalisandeep.cvgen.security.UserPrinciple;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OAuth2ExchangeCodeStore {

	private final Map<String, StoredValue<UserPrinciple>> usersByUsername = new ConcurrentHashMap<>();
	private final Map<String, StoredValue<String>> issuedTokens = new ConcurrentHashMap<>();
	private final Map<String, StoredValue<String>> oauthStates = new ConcurrentHashMap<>();

	public void saveUser(UserPrinciple userPrinciple) {
		if (userPrinciple == null || !StringUtils.hasText(userPrinciple.getUsername())) {
			return;
		}
		usersByUsername.put(userPrinciple.getUsername(), new StoredValue<>(userPrinciple, null));
	}

	public Optional<UserPrinciple> findUserByUsername(String username) {
		cleanupExpired();
		StoredValue<UserPrinciple> stored = usersByUsername.get(username);
		if (stored == null) {
			return Optional.empty();
		}
		return Optional.of(stored.value());
	}

	public void storeIssuedToken(String username, String token, Duration ttl) {
		if (!StringUtils.hasText(username) || !StringUtils.hasText(token)) {
			return;
		}
		issuedTokens.put(token, new StoredValue<>(username, expiration(ttl)));
	}

	public Optional<String> consumeIssuedToken(String token) {
		cleanupExpired();
		StoredValue<String> value = issuedTokens.remove(token);
		return value == null ? Optional.empty() : Optional.of(value.value());
	}

	public void storeOAuthState(String state, String value, Duration ttl) {
		if (!StringUtils.hasText(state) || !StringUtils.hasText(value)) {
			return;
		}
		oauthStates.put(state, new StoredValue<>(value, expiration(ttl)));
	}

	public Optional<String> consumeOAuthState(String state) {
		cleanupExpired();
		StoredValue<String> stored = oauthStates.remove(state);
		return stored == null ? Optional.empty() : Optional.of(stored.value());
	}

	public void clear() {
		usersByUsername.clear();
		issuedTokens.clear();
		oauthStates.clear();
	}

	private void cleanupExpired() {
		Instant now = Instant.now();
		usersByUsername.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
		issuedTokens.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
		oauthStates.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
	}

	private Instant expiration(Duration ttl) {
		return ttl == null ? null : Instant.now().plus(ttl);
	}

	private record StoredValue<T>(T value, Instant expiresAt) {
		private boolean isExpired(Instant now) {
			return expiresAt != null && now.isAfter(expiresAt);
		}
	}
}

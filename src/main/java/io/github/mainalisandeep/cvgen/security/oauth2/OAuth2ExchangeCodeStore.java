package io.github.mainalisandeep.cvgen.security.oauth2;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OAuth2ExchangeCodeStore {

    private final Map<String, StoredValue<UUID>> exchangeCodes = new ConcurrentHashMap<>();

    public void storeExchangeCode(String code, UUID userId, Duration ttl) {
        if (!StringUtils.hasText(code) || userId == null) {
            return;
        }
        exchangeCodes.put(code, new StoredValue<>(userId, expiration(ttl)));
    }

    public Optional<UUID> consumeExchangeCode(String code) {
        cleanupExpired();
        StoredValue<UUID> stored = exchangeCodes.remove(code);
        return stored == null ? Optional.empty() : Optional.of(stored.value());
    }

    public void clear() {
        exchangeCodes.clear();
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        exchangeCodes.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
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

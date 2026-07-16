package io.github.mainalisandeep.cvgen.records;

import java.time.Instant;

public record StoredValue<T>(T value, Instant expiresAt) {
    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }
}
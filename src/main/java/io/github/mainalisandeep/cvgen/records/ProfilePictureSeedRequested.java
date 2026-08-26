package io.github.mainalisandeep.cvgen.records;

import java.util.UUID;

/**
 * Raised when an account is created through a provider that reports an avatar.
 * <p>
 * Seeding is an event rather than a direct call so the download happens after the signup
 * transaction commits and off the login request thread: an unreachable provider CDN must not
 * add latency to authentication, let alone fail it.
 */
public record ProfilePictureSeedRequested(UUID userId, UUID identityId) {
}

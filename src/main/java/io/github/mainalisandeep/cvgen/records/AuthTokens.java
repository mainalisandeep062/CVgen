package io.github.mainalisandeep.cvgen.records;

/**
 * Credentials minted for an authenticated user.
 *
 * @param accessToken        bearer token returned in the response body
 * @param refreshToken       set as an HttpOnly cookie, never returned in the body
 * @param trustedDeviceToken raw remember-me token, or {@code null} when not requested
 */
public record AuthTokens(String accessToken, String refreshToken, String trustedDeviceToken) {

    public boolean hasTrustedDeviceToken() {
        return trustedDeviceToken != null && !trustedDeviceToken.isBlank();
    }
}

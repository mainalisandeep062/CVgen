package io.github.mainalisandeep.cvgen.records;

/**
 * Outcome of a password login: either tokens (trusted device) or an OTP challenge.
 */
public record LoginResult(boolean otpRequired, AuthTokens tokens) {

    public static LoginResult authenticated(AuthTokens tokens) {
        return new LoginResult(false, tokens);
    }

    public static LoginResult otpChallenge() {
        return new LoginResult(true, null);
    }
}

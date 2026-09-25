package io.github.mainalisandeep.cvgen.enums;

/**
 * Whether an account may be used at all. Persisted by name in {@code users.status}.
 */
public enum UserStatus {

    ACTIVE,

    /** Refused at every token issue and on every authenticated request, whatever token it holds. */
    SUSPENDED
}

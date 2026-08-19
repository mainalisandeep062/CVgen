package io.github.mainalisandeep.cvgen.enums;

public enum RevocationReason {
    LOGOUT,
    ROTATED,
    REUSE_DETECTED,
    /** Sibling of a replayed token: revoked because its family is compromised, not because it was itself replayed. */
    FAMILY_COMPROMISED,
    LOGOUT_ALL,
    EXPIRED_CLEANUP
}

package io.github.mainalisandeep.cvgen.enums;

import java.util.List;

/**
 * What a user may do. Persisted by name in {@code users.role}.
 * <p>
 * Roles are cumulative rather than exclusive: an admin is also a user, so every endpoint guarded by
 * {@code ROLE_USER} keeps working for admins without listing both roles.
 */
public enum UserRole {

    USER(List.of("ROLE_USER")),
    ADMIN(List.of("ROLE_USER", "ROLE_ADMIN"));

    private final List<String> authorities;

    UserRole(List<String> authorities) {
        this.authorities = authorities;
    }

    /** Authority names carried in the JWT {@code authorities} claim. */
    public List<String> getAuthorities() {
        return authorities;
    }
}

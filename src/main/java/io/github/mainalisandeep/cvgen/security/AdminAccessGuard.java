package io.github.mainalisandeep.cvgen.security;

import io.github.mainalisandeep.cvgen.enums.UserRole;
import io.github.mainalisandeep.cvgen.enums.UserStatus;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.security.util.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Second half of admin authorization, referenced from {@code @PreAuthorize} on every admin controller.
 * <p>
 * {@code hasRole('ADMIN')} in {@code SecurityConfig} reads the token, and the token is a snapshot from
 * when it was issued. An admin demoted or suspended a minute ago still holds a valid token with
 * {@code ROLE_ADMIN} in it, so this re-reads role and status from the database on every admin request.
 * The URL rule stays as the cheap first filter that keeps ordinary users away from the query.
 */
@Component("adminAccessGuard")
@RequiredArgsConstructor
public class AdminAccessGuard {

    /** The {@code @PreAuthorize} expression every admin controller carries. */
    public static final String IS_ACTIVE_ADMIN = "@adminAccessGuard.isActiveAdmin()";

    private final UserRepository userRepository;
    private final JwtTokenUtil jwtTokenUtil;

    public boolean isActiveAdmin() {
        return userRepository.existsByIdAndRoleAndStatus(
                jwtTokenUtil.getCurrentUserId(), UserRole.ADMIN, UserStatus.ACTIVE);
    }
}

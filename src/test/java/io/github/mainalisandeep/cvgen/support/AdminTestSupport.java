package io.github.mainalisandeep.cvgen.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.enums.UserRole;
import io.github.mainalisandeep.cvgen.enums.UserStatus;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Shared fixtures for the admin endpoint tests.
 *
 * <p>Users always get a unique email and are never wiped between tests: another test class may hold
 * rows that reference them, and every assertion here is scoped to an id anyway.
 *
 * <p>{@link #as(User)} derives authorities from the stored role exactly as {@code UserMapper} does, so
 * a test principal matches the token a real login would produce. {@link #with(User, String...)} exists
 * for the one case that must not match: a token issued before a demotion.
 */
public abstract class AdminTestSupport extends PostgresContainerSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    protected User saveUser(String prefix) {
        return saveUser(prefix, UserRole.USER, UserStatus.ACTIVE);
    }

    protected User saveAdmin(String prefix) {
        return saveUser(prefix, UserRole.ADMIN, UserStatus.ACTIVE);
    }

    protected User saveUser(String prefix, UserRole role, UserStatus status) {
        return userRepository.save(User.builder()
                .email(prefix + "-" + UUID.randomUUID() + "@test.com")
                .name(prefix)
                .emailVerified(true)
                .role(role)
                .status(status)
                .build());
    }

    /** Authenticates as {@code user} with the authorities their stored role grants. */
    protected RequestPostProcessor as(User user) {
        return with(user, user.getRole().getAuthorities().toArray(String[]::new));
    }

    /** Authenticates as {@code user} with authorities given explicitly, whatever the database says. */
    protected RequestPostProcessor with(User user, String... authorities) {
        List<SimpleGrantedAuthority> granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        UserPrincipal principal = UserPrincipal.localUser(
                user.getId().toString(),
                user.getName(),
                user.getEmail(),
                user.getEmail(),
                user.getPasswordHash(),
                null,
                granted
        );
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .authentication(new UsernamePasswordAuthenticationToken(principal, null, granted));
    }
}

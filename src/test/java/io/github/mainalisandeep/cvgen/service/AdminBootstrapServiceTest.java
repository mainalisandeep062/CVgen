package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.enums.UserRole;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.service.impl.AdminBootstrapService;
import io.github.mainalisandeep.cvgen.support.PostgresContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How the first administrator comes to exist.
 *
 * <p>The verified-email rule is the point: an unverified local signup proves nothing about who owns
 * the address, so promoting one would hand admin to whoever typed it first.
 */
@SpringBootTest(properties = "app.admin.bootstrap-emails=Bootstrap-Admin@test.com, unverified@test.com")
@ActiveProfiles("test")
@Transactional
class AdminBootstrapServiceTest extends PostgresContainerSupport {

    @Autowired
    private AdminBootstrapService adminBootstrapService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("A listed, verified account is promoted, and the comparison ignores case")
    void promotesListedVerifiedAccount() {
        User user = save("bootstrap-admin@test.com", true);

        adminBootstrapService.promoteIfListed(user);

        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("A listed account with an unverified email is not promoted")
    void leavesUnverifiedAccountAlone() {
        User user = save("unverified@test.com", false);

        adminBootstrapService.promoteIfListed(user);

        assertThat(user.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("An account that is not listed is not promoted")
    void leavesUnlistedAccountAlone() {
        User user = save("someone-" + UUID.randomUUID() + "@test.com", true);

        adminBootstrapService.promoteIfListed(user);

        assertThat(user.getRole()).isEqualTo(UserRole.USER);
    }

    private User save(String email, boolean verified) {
        userRepository.findByEmail(email).ifPresent(userRepository::delete);
        return userRepository.save(User.builder()
                .email(email)
                .name("Bootstrap")
                .emailVerified(verified)
                .build());
    }
}

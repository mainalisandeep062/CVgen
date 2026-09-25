package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.common.exception.ConflictException;
import io.github.mainalisandeep.cvgen.dto.AdminUserUpdateRequestDto;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.enums.UserRole;
import io.github.mainalisandeep.cvgen.enums.UserStatus;
import io.github.mainalisandeep.cvgen.support.AdminTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The last-admin invariant, at the service layer.
 *
 * <p>Deliberately not through the API: {@code AdminAccessGuard} makes sure the caller is an active
 * admin, and an admin may not change their own role or status, so a second active admin always exists
 * by the time a request arrives. The invariant still has to hold, because two admins acting on each
 * other at the same moment - or any future caller that is not a request - can reach it.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AdminUserServiceTest extends AdminTestSupport {

    @Autowired
    private AdminUserService adminUserService;

    private User lastAdmin;
    private User actor;

    @BeforeEach
    void setUp() {
        // Whatever the database holds, this transaction sees exactly one active admin.
        userRepository.findAllByRoleAndStatusForUpdate(UserRole.ADMIN, UserStatus.ACTIVE)
                .forEach(existing -> existing.setRole(UserRole.USER));

        lastAdmin = saveAdmin("last-admin");
        actor = saveUser("last-admin-actor");
    }

    @Test
    @DisplayName("Demoting the last active admin conflicts")
    void demotingLastAdminConflicts() {
        var request = AdminUserUpdateRequestDto.builder().role(UserRole.USER).build();

        assertThatThrownBy(() -> adminUserService.update(actor.getId(), lastAdmin.getId(), request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Suspending the last active admin conflicts")
    void suspendingLastAdminConflicts() {
        var request = AdminUserUpdateRequestDto.builder().status(UserStatus.SUSPENDED).build();

        assertThatThrownBy(() -> adminUserService.update(actor.getId(), lastAdmin.getId(), request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Deleting the last active admin conflicts")
    void deletingLastAdminConflicts() {
        assertThatThrownBy(() -> adminUserService.delete(actor.getId(), lastAdmin.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("With a second active admin the same demotion succeeds")
    void demotingOneOfTwoAdminsSucceeds() {
        saveAdmin("second-admin");

        var request = AdminUserUpdateRequestDto.builder().role(UserRole.USER).build();
        adminUserService.update(actor.getId(), lastAdmin.getId(), request);

        assertThatThrownBy(() -> adminUserService.delete(actor.getId(), actor.getId()))
                .isInstanceOf(io.github.mainalisandeep.cvgen.common.exception.BadRequestException.class);
    }

    @Test
    @DisplayName("Renaming the last admin is not a role or status change, so it is allowed")
    void renamingLastAdminIsAllowed() {
        var request = AdminUserUpdateRequestDto.builder().name("Still Admin").build();

        adminUserService.update(actor.getId(), lastAdmin.getId(), request);
    }
}

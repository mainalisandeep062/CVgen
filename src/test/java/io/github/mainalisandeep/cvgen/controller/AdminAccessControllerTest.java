package io.github.mainalisandeep.cvgen.controller;

import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.enums.UserRole;
import io.github.mainalisandeep.cvgen.enums.UserStatus;
import io.github.mainalisandeep.cvgen.support.AdminTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may reach {@code /api/admin/**}, and what a suspended account may do anywhere.
 *
 * <p>The case worth the whole file is {@link #demotedAdminIsRefusedDespiteAdminAuthority()}: access
 * tokens live for two hours and cannot be recalled, so an admin demoted a minute ago still holds a
 * token that says {@code ROLE_ADMIN}. The URL rule alone would let that token through, which is why
 * every admin controller re-checks the database.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AdminAccessControllerTest extends AdminTestSupport {

    private User admin;
    private User member;

    @BeforeEach
    void setUp() {
        admin = saveAdmin("access-admin");
        member = saveUser("access-member");
    }

    @ParameterizedTest(name = "{0} requires authentication")
    @ValueSource(strings = {
            "/api/admin/analytics/overview",
            "/api/admin/users",
            "/api/admin/templates",
            "/api/admin/billing/packs",
            "/api/admin/notifications",
            "/api/admin/audit-logs"
    })
    @DisplayName("Every admin route group is unauthorized without a token")
    void adminRoutesRejectAnonymous(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(false));
    }

    @ParameterizedTest(name = "{0} is forbidden for a plain user")
    @ValueSource(strings = {
            "/api/admin/analytics/overview",
            "/api/admin/users",
            "/api/admin/templates",
            "/api/admin/billing/packs",
            "/api/admin/notifications",
            "/api/admin/audit-logs"
    })
    @DisplayName("Every admin route group is forbidden for ROLE_USER")
    void adminRoutesRejectPlainUser(String path) throws Exception {
        mockMvc.perform(get(path).with(as(member)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(false));
    }

    @ParameterizedTest(name = "{0} is allowed for an active admin")
    @ValueSource(strings = {
            "/api/admin/analytics/overview",
            "/api/admin/users",
            "/api/admin/templates",
            "/api/admin/billing/packs",
            "/api/admin/notifications",
            "/api/admin/audit-logs"
    })
    @DisplayName("Every admin route group answers an active admin")
    void adminRoutesAllowActiveAdmin(String path) throws Exception {
        mockMvc.perform(get(path).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true));
    }

    @Test
    @DisplayName("An admin demoted in the database is refused even while holding a ROLE_ADMIN token")
    void demotedAdminIsRefusedDespiteAdminAuthority() throws Exception {
        admin.setRole(UserRole.USER);
        userRepository.save(admin);

        mockMvc.perform(get("/api/admin/users").with(with(admin, "ROLE_USER", "ROLE_ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(false));
    }

    @Test
    @DisplayName("A suspended admin is refused even while holding a ROLE_ADMIN token")
    void suspendedAdminIsRefused() throws Exception {
        admin.setStatus(UserStatus.SUSPENDED);
        userRepository.save(admin);

        mockMvc.perform(get("/api/admin/users").with(as(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A suspended user is refused on an ordinary endpoint, with the suspension message")
    void suspendedUserIsRefusedOnOrdinaryEndpoint() throws Exception {
        member.setStatus(UserStatus.SUSPENDED);
        userRepository.save(member);

        mockMvc.perform(get("/api/cvs").with(as(member)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.message").value("This account has been suspended"));
    }

    @Test
    @DisplayName("A token for an account that no longer exists is refused the same way")
    void deletedUserIsRefused() throws Exception {
        User ghost = saveUser("access-ghost");
        userRepository.delete(ghost);

        mockMvc.perform(get("/api/cvs").with(as(ghost)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This account has been suspended"));
    }

    @Test
    @DisplayName("An active user still passes the per-request status check")
    void activeUserPasses() throws Exception {
        mockMvc.perform(get("/api/cvs").with(as(member)))
                .andExpect(status().isOk());
    }
}

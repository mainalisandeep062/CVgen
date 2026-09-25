package io.github.mainalisandeep.cvgen.controller;

import io.github.mainalisandeep.cvgen.dto.AdminUserUpdateRequestDto;
import io.github.mainalisandeep.cvgen.dto.CreditAdjustmentRequestDto;
import io.github.mainalisandeep.cvgen.dto.LoginRequestDto;
import io.github.mainalisandeep.cvgen.entity.Cv;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.entity.UserIdentity;
import io.github.mainalisandeep.cvgen.enums.UserRole;
import io.github.mainalisandeep.cvgen.enums.UserStatus;
import io.github.mainalisandeep.cvgen.config.CvProperties;
import io.github.mainalisandeep.cvgen.repository.CvRepository;
import io.github.mainalisandeep.cvgen.repository.RefreshTokenRepository;
import io.github.mainalisandeep.cvgen.repository.UserIdentityRepository;
import io.github.mainalisandeep.cvgen.service.impl.RefreshTokenService;
import io.github.mainalisandeep.cvgen.support.AdminTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Account administration over {@code /api/admin/users}.
 *
 * <p>The last-admin invariant is exercised in {@code AdminUserServiceTest} instead: through the API it
 * cannot be reached, because the acting admin is active and may not change themselves, so a second
 * active admin always exists.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AdminUserControllerTest extends AdminTestSupport {

    @Autowired
    private CvRepository cvRepository;

    @Autowired
    private CvProperties cvProperties;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User admin;
    private User member;

    @BeforeEach
    void setUp() {
        admin = saveAdmin("users-admin");
        member = saveUser("users-member");
    }

    // --- Search ---

    @Test
    @DisplayName("Search matches email and name case-insensitively")
    void searchMatchesEmailAndName() throws Exception {
        String token = UUID.randomUUID().toString().substring(0, 8);
        User match = saveUser("needle" + token);

        mockMvc.perform(get("/api/admin/users").param("q", "NEEDLE" + token.toUpperCase()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].userId").value(match.getId().toString()))
                .andExpect(jsonPath("$.data.items[0].role").value(UserRole.USER.name()))
                .andExpect(jsonPath("$.data.items[0].status").value(UserStatus.ACTIVE.name()))
                .andExpect(jsonPath("$.data.items[0].cvCount").value(0))
                .andExpect(jsonPath("$.data.items[0].creditBalance").value(0));
    }

    @Test
    @DisplayName("Filtering by status returns only suspended accounts")
    void searchFiltersByStatus() throws Exception {
        String token = UUID.randomUUID().toString().substring(0, 8);
        User suspended = saveUser("filter" + token, UserRole.USER, UserStatus.SUSPENDED);
        saveUser("filter" + token);

        mockMvc.perform(get("/api/admin/users")
                        .param("q", "filter" + token)
                        .param("status", UserStatus.SUSPENDED.name())
                        .with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].userId").value(suspended.getId().toString()));
    }

    @Test
    @DisplayName("Sorting by a property that is not whitelisted is rejected")
    void searchRejectsUnsupportedSort() throws Exception {
        mockMvc.perform(get("/api/admin/users").param("sort", "passwordHash,desc").with(as(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(false));
    }

    @Test
    @DisplayName("A user id that does not exist is 404")
    void getUnknownUserIsNotFound() throws Exception {
        mockMvc.perform(get("/api/admin/users/{userId}", UUID.randomUUID()).with(as(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Detail carries the account plus its recent CVs")
    void getUserDetail() throws Exception {
        persistCv(member, "Portfolio");

        mockMvc.perform(get("/api/admin/users/{userId}", member.getId()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(member.getEmail()))
                .andExpect(jsonPath("$.data.cvCount").value(1))
                .andExpect(jsonPath("$.data.recentCvs[0].title").value("Portfolio"))
                .andExpect(jsonPath("$.data.recentTransactions").isArray());
    }

    // --- Update ---

    @Test
    @DisplayName("An admin cannot change their own role")
    void selfRoleChangeIsRejected() throws Exception {
        var request = AdminUserUpdateRequestDto.builder().role(UserRole.USER).build();

        mockMvc.perform(patch("/api/admin/users/{userId}", admin.getId())
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(false));

        assertThat(userRepository.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("An admin cannot suspend themselves")
    void selfSuspensionIsRejected() throws Exception {
        var request = AdminUserUpdateRequestDto.builder().status(UserStatus.SUSPENDED).build();

        mockMvc.perform(patch("/api/admin/users/{userId}", admin.getId())
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("An admin may rename themselves: only role and status are off limits")
    void selfRenameIsAllowed() throws Exception {
        var request = AdminUserUpdateRequestDto.builder().name("Renamed Admin").build();

        mockMvc.perform(patch("/api/admin/users/{userId}", admin.getId())
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Renamed Admin"));
    }

    @Test
    @DisplayName("Suspending a user revokes every refresh token they hold")
    void suspendRevokesRefreshTokens() throws Exception {
        refreshTokenService.issueNewFamily(member);
        assertThat(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(member.getId())).isNotEmpty();

        var request = AdminUserUpdateRequestDto.builder().status(UserStatus.SUSPENDED).build();

        mockMvc.perform(patch("/api/admin/users/{userId}", member.getId())
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(UserStatus.SUSPENDED.name()));

        assertThat(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(member.getId())).isEmpty();
    }

    @Test
    @DisplayName("A suspended account cannot log in with its password")
    void suspendedUserCannotLogIn() throws Exception {
        User local = userRepository.save(User.builder()
                .email("suspended-login-" + UUID.randomUUID() + "@test.com")
                .name("Suspended Login")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(true)
                .status(UserStatus.SUSPENDED)
                .build());

        var request = new LoginRequestDto(local.getEmail(), "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This account has been suspended"));
    }

    @Test
    @DisplayName("A wrong password on a suspended account still reads as invalid credentials")
    void suspendedUserWithWrongPasswordIsUnauthorized() throws Exception {
        User local = userRepository.save(User.builder()
                .email("suspended-wrong-" + UUID.randomUUID() + "@test.com")
                .name("Suspended Wrong")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(true)
                .status(UserStatus.SUSPENDED)
                .build());

        var request = new LoginRequestDto(local.getEmail(), "not-the-password");

        // The status check sits after the password check on purpose: answering 403 here would tell a
        // caller without the password that the address is registered.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // --- Credits ---

    @Test
    @DisplayName("Granting credits moves the balance and writes a ledger row")
    void grantCredits() throws Exception {
        var request = CreditAdjustmentRequestDto.builder().credits(25).note("Support goodwill").build();

        mockMvc.perform(post("/api/admin/users/{userId}/credits", member.getId())
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("ADMIN_GRANT"))
                .andExpect(jsonPath("$.data.credits").value(25))
                .andExpect(jsonPath("$.data.balanceAfter").value(25))
                .andExpect(jsonPath("$.data.createdByEmail").value(admin.getEmail()));

        assertThat(userRepository.findById(member.getId()).orElseThrow().getCreditBalance()).isEqualTo(25);
    }

    @Test
    @DisplayName("Deducting credits writes a deduction and lowers the balance")
    void deductCredits() throws Exception {
        adjust(member, 30, "Seed");

        var request = CreditAdjustmentRequestDto.builder().credits(-10).note("Chargeback").build();

        mockMvc.perform(post("/api/admin/users/{userId}/credits", member.getId())
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("ADMIN_DEDUCT"))
                .andExpect(jsonPath("$.data.balanceAfter").value(20));

        assertThat(userRepository.findById(member.getId()).orElseThrow().getCreditBalance()).isEqualTo(20);
    }

    @Test
    @DisplayName("A deduction below zero is rejected and the balance is untouched")
    void deductionBelowZeroIsRejected() throws Exception {
        adjust(member, 5, "Seed");

        var request = CreditAdjustmentRequestDto.builder().credits(-10).note("Too much").build();

        mockMvc.perform(post("/api/admin/users/{userId}/credits", member.getId())
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(false));

        assertThat(userRepository.findById(member.getId()).orElseThrow().getCreditBalance()).isEqualTo(5);
    }

    @Test
    @DisplayName("A zero adjustment, and one without a note, fail validation")
    void invalidAdjustmentsAreRejected() throws Exception {
        mockMvc.perform(post("/api/admin/users/{userId}/credits", member.getId())
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                CreditAdjustmentRequestDto.builder().credits(0).note("Nothing").build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());

        mockMvc.perform(post("/api/admin/users/{userId}/credits", member.getId())
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                CreditAdjustmentRequestDto.builder().credits(5).build())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A user's own profile carries their role, status and credit balance")
    void ownProfileCarriesRoleStatusAndBalance() throws Exception {
        adjust(member, 7, "Seed");

        mockMvc.perform(get("/api/users/me").with(as(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value(UserRole.USER.name()))
                .andExpect(jsonPath("$.data.status").value(UserStatus.ACTIVE.name()))
                .andExpect(jsonPath("$.data.creditBalance").value(7));

        mockMvc.perform(get("/api/users/me").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value(UserRole.ADMIN.name()));
    }

    // --- Delete ---

    @Test
    @DisplayName("Deleting a user removes their identities, refresh tokens and CVs with them")
    void deleteUserWithDependents() throws Exception {
        userIdentityRepository.save(UserIdentity.builder()
                .user(member)
                .provider("google")
                .providerId("google-" + UUID.randomUUID())
                .emailAtProvider(member.getEmail())
                .build());
        refreshTokenService.issueNewFamily(member);
        persistCv(member, "Doomed");
        adjust(member, 10, "Seed");

        mockMvc.perform(delete("/api/admin/users/{userId}", member.getId()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true));

        assertThat(userRepository.findById(member.getId())).isEmpty();
        assertThat(userIdentityRepository.findByUserId(member.getId())).isEmpty();
        assertThat(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(member.getId())).isEmpty();
        assertThat(cvRepository.countByUserId(member.getId())).isZero();
    }

    @Test
    @DisplayName("An admin cannot delete their own account")
    void selfDeleteIsRejected() throws Exception {
        mockMvc.perform(delete("/api/admin/users/{userId}", admin.getId()).with(as(admin)))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findById(admin.getId())).isPresent();
    }

    // --- Helpers ---

    private void adjust(User user, int credits, String note) throws Exception {
        mockMvc.perform(post("/api/admin/users/{userId}/credits", user.getId())
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                CreditAdjustmentRequestDto.builder().credits(credits).note(note).build())))
                .andExpect(status().isCreated());
    }

    private Cv persistCv(User user, String title) {
        return cvRepository.save(Cv.builder()
                .user(user)
                .title(title)
                .templateKey(cvProperties.getDefaultTemplateKey())
                .locale(cvProperties.getDefaultLocale())
                .content(objectMapper.createObjectNode().put("schemaVersion", 1))
                .build());
    }
}

package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.config.SecurityProperties;
import io.github.mainalisandeep.cvgen.entity.RefreshToken;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.enums.RevocationReason;
import io.github.mainalisandeep.cvgen.repository.RefreshTokenRepository;
import io.github.mainalisandeep.cvgen.repository.UserIdentityRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.security.JwtTokenProvider;
import io.github.mainalisandeep.cvgen.service.impl.RefreshTokenService;
import io.github.mainalisandeep.cvgen.support.PostgresContainerSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Refresh-token theft, end to end: A's token is stolen and rotated by the thief, then A replays it.
 *
 * <p>Deliberately <b>not</b> {@code @Transactional}. Reuse detection revokes the family in a
 * {@code REQUIRES_NEW} transaction and then throws, so the whole point is which writes survive a
 * commit boundary — a test-managed transaction wrapping the request would hide that. Cleanup is
 * therefore explicit in {@link #setUp()}.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RefreshTokenReuseDetectionTest extends PostgresContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SecurityProperties securityProperties;

    @BeforeEach
    void setUp() {
        // refresh tokens first: they hold the FK to users
        refreshTokenRepository.deleteAll();
        userIdentityRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("A replaying a stolen-and-rotated token revokes the whole family, thief's token included")
    void replayOfStolenTokenRevokesFamily() throws Exception {
        User victim = saveUser("victim@test.com");
        User bystander = saveUser("bystander@test.com");

        String victimToken = refreshTokenService.issueNewFamily(victim);
        String bystanderToken = refreshTokenService.issueNewFamily(bystander);

        // the thief pastes the victim's cookie into their own browser and refreshes.
        // Nothing distinguishes them from the victim, so this succeeds by design.
        String thiefToken = refreshCookieOf(mockMvc.perform(refresh(victimToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(thiefToken).isNotBlank().isNotEqualTo(victimToken);

        // the victim's browser refreshes next, replaying the token the thief already rotated
        mockMvc.perform(refresh(victimToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.message").value("Refresh token has already been used or is invalid"));

        RefreshToken replayed = findByRawToken(victimToken);
        assertThat(replayed.getRevokedAt()).isNotNull();
        assertThat(replayed.getRevocationReason()).isEqualTo(RevocationReason.REUSE_DETECTED);

        // the thief's token is a sibling, not the replayed one: killed, but under its own reason
        RefreshToken stolen = findByRawToken(thiefToken);
        assertThat(stolen.getRevokedAt()).isNotNull();
        assertThat(stolen.getRevocationReason()).isEqualTo(RevocationReason.FAMILY_COMPROMISED);
        assertThat(stolen.getFamilyId()).isEqualTo(replayed.getFamilyId());

        // and it no longer works
        mockMvc.perform(refresh(thiefToken))
                .andExpect(status().isUnauthorized());

        // an unrelated user's session is untouched
        RefreshToken other = findByRawToken(bystanderToken);
        assertThat(other.getRevokedAt()).isNull();
        assertThat(other.getRevocationReason()).isNull();
    }

    @Test
    @DisplayName("A stolen but not-yet-rotated token still rotates: theft alone is indistinguishable")
    void stolenTokenRotatesUntilReplayed() throws Exception {
        User victim = saveUser("firstuse@test.com");
        String victimToken = refreshTokenService.issueNewFamily(victim);

        mockMvc.perform(refresh(victimToken)).andExpect(status().isOk());

        RefreshToken used = findByRawToken(victimToken);
        assertThat(used.getRevocationReason()).isEqualTo(RevocationReason.ROTATED);
        assertThat(used.getReplacedById()).isNotNull();
    }

    private User saveUser(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .name(email)
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(true)
                .build());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder refresh(String rawToken) {
        return post("/api/auth/refresh")
                .cookie(new Cookie(securityProperties.getOauth2().getAccessTokenCookieName(), rawToken));
    }

    /** The rotated token only ever comes back as a Set-Cookie, never in the body. */
    private String refreshCookieOf(MvcResult result) {
        Cookie cookie = result.getResponse()
                .getCookie(securityProperties.getOauth2().getAccessTokenCookieName());
        return cookie == null ? null : cookie.getValue();
    }

    private RefreshToken findByRawToken(String rawToken) {
        UUID jti = jwtTokenProvider.getTokenIdFromToken(rawToken);
        return refreshTokenRepository.findByJti(jti).orElseThrow();
    }
}

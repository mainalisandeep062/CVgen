package io.github.mainalisandeep.cvgen.security.oauth2;

import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.entity.UserIdentity;
import io.github.mainalisandeep.cvgen.repository.UserIdentityRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OAuth2UserResolverTest {

    @Autowired
    private OAuth2UserResolver resolver;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @BeforeEach
    void setUp() {
        userIdentityRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("AC1+AC2: New Google login creates user and identity; second GitHub login with same verified email auto-links")
    void googleThenGithubAutoLink() {
        // Step 1: Google login (verified email)
        Map<String, Object> googleAttrs = Map.of(
                "sub", "google-123",
                "name", "Test User",
                "email", "test@example.com",
                "email_verified", true,
                "picture", "http://pic.jpg"
        );

        User googleUser = resolver.resolve("google", googleAttrs);
        assertThat(googleUser.getEmail()).isEqualTo("test@example.com");
        assertThat(googleUser.isEmailVerified()).isTrue();

        var identities = userIdentityRepository.findAll();
        assertThat(identities).hasSize(1);
        assertThat(identities.get(0).getProvider()).isEqualTo("google");

        // Step 2: GitHub login with same verified email — should auto-link
        Map<String, Object> githubAttrs = Map.of(
                "id", "github-456",
                "login", "testuser",
                "name", "Test User",
                "email", "test@example.com",
                "email_verified", true,
                "avatar_url", "http://avatar.jpg"
        );

        User githubUser = resolver.resolve("github", githubAttrs);

        // Same user, not a new row
        assertThat(githubUser.getId()).isEqualTo(googleUser.getId());

        // Now has two identities
        identities = userIdentityRepository.findAll();
        assertThat(identities).hasSize(2);
        assertThat(identities).anyMatch(i -> i.getProvider().equals("google"));
        assertThat(identities).anyMatch(i -> i.getProvider().equals("github"));
    }

    @Test
    @DisplayName("AC3: GitHub login with unverified email matching existing account is rejected")
    void githubUnverifiedEmailConflict() {
        // First: Create user via Google with verified email
        User existing = User.builder()
                .email("conflict@example.com")
                .name("Existing")
                .emailVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userRepository.save(existing);

        // Then: GitHub login with same email but unverified
        Map<String, Object> githubAttrs = Map.of(
                "id", "github-789",
                "login", "conflictuser",
                "email", "conflict@example.com",
                "email_verified", false
        );

        assertThatThrownBy(() -> resolver.resolve("github", githubAttrs))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> {
                    OAuth2AuthenticationException oa2e = (OAuth2AuthenticationException) ex;
                    assertThat(oa2e.getError().getErrorCode()).isEqualTo("email_unverified_conflict");
                });
    }

    @Test
    @DisplayName("GitHub login with verified email matching existing unverified local user links and sets emailVerified=true")
    void githubVerifiedLinksLocalUser() {
        // Local user signed up but not yet verified
        User localUser = User.builder()
                .email("local@example.com")
                .name("Local")
                .passwordHash("hashed")
                .emailVerified(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userRepository.save(localUser);

        Map<String, Object> githubAttrs = Map.of(
                "id", "github-999",
                "login", "localuser",
                "email", "local@example.com",
                "email_verified", true
        );

        User resolved = resolver.resolve("github", githubAttrs);
        assertThat(resolved.getId()).isEqualTo(localUser.getId());
        assertThat(resolved.isEmailVerified()).isTrue();

        var identities = userIdentityRepository.findAll();
        assertThat(identities).hasSize(1);
        assertThat(identities.get(0).getProvider()).isEqualTo("github");
    }

    @Test
    @DisplayName("Re-login with same identity returns same user without creating duplicate identity")
    void reLoginSameIdentity() {
        Map<String, Object> googleAttrs = Map.of(
                "sub", "google-111",
                "name", "Repeat",
                "email", "repeat@example.com",
                "email_verified", true
        );

        User first = resolver.resolve("google", googleAttrs);
        User second = resolver.resolve("google", googleAttrs);

        assertThat(first.getId()).isEqualTo(second.getId());
        assertThat(userIdentityRepository.findAll()).hasSize(1);
    }
}

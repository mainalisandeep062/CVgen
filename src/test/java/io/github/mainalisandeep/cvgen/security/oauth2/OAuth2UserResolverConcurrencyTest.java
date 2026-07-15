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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OAuth2UserResolverConcurrencyTest {

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
    @DisplayName("Concurrent duplicate login race: only one identity created, no duplicate users")
    void concurrentDuplicateLogin() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        Map<String, Object> githubAttrs = Map.of(
                "id", "github-concurrent-123",
                "login", "concurrentuser",
                "name", "Concurrent",
                "email", "concurrent@example.com",
                "email_verified", true,
                "avatar_url", "http://avatar.jpg"
        );

        List<Future<?>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await(); // All threads start at the same time
                    resolver.resolve("github", githubAttrs);
                    successCount.incrementAndGet();
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                    // Expected: unique constraint on (provider, provider_id) prevents duplicates
                    exceptionCount.incrementAndGet();
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                }
            }));
        }

        // Release all threads simultaneously
        latch.countDown();

        for (Future<?> f : futures) {
            f.get();
        }

        executor.shutdown();

        // Exactly one user should exist
        List<User> users = userRepository.findAll();
        assertThat(users).hasSize(1);
        assertThat(users.get(0).getEmail()).isEqualTo("concurrent@example.com");

        // Exactly one identity should exist
        List<UserIdentity> identities = userIdentityRepository.findAll();
        assertThat(identities).hasSize(1);
        assertThat(identities.get(0).getProvider()).isEqualTo("github");

        // Some threads may have hit the unique constraint, but data is consistent
        assertThat(successCount.get() + exceptionCount.get()).isEqualTo(threadCount);
    }
}

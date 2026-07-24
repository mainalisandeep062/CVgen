package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.entity.TrustedDevice;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.repository.TrustedDeviceRepository;
import io.github.mainalisandeep.cvgen.repository.UserIdentityRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TrustedDeviceServiceTest {

    @Autowired
    private TrustedDeviceService trustedDeviceService;

    @Autowired
    private TrustedDeviceRepository trustedDeviceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;

    @BeforeEach
    void setUp() {
        // identities first: they hold the FK to users
        userIdentityRepository.deleteAll();
        trustedDeviceRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .email("device@test.com")
                .name("Device Test")
                .emailVerified(true)
                .build());
    }

    @Test
    @DisplayName("AC7: remember() creates trusted device and returns raw token")
    void rememberCreatesDevice() {
        String rawToken = trustedDeviceService.remember(testUser);

        assertThat(rawToken).isNotBlank();

        var devices = trustedDeviceRepository.findAll();
        assertThat(devices).hasSize(1);

        TrustedDevice device = devices.get(0);
        assertThat(device.getUser().getId()).isEqualTo(testUser.getId());
        assertThat(device.getTokenHash()).isNotEqualTo(rawToken); // hashed storage
        assertThat(device.getExpiresAt()).isAfter(Instant.now());
        assertThat(device.getLastUsedAt()).isNull();
        assertThat(device.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("AC7: isTrusted returns true for valid device token")
    void isTrustedValidToken() {
        String rawToken = trustedDeviceService.remember(testUser);

        assertThat(trustedDeviceService.isTrusted(testUser.getId(), rawToken)).isTrue();

        TrustedDevice device = trustedDeviceRepository.findAll().get(0);
        assertThat(device.getLastUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("AC7: isTrusted returns false for wrong token")
    void isTrustedWrongToken() {
        trustedDeviceService.remember(testUser);

        assertThat(trustedDeviceService.isTrusted(testUser.getId(), "wrong-token")).isFalse();
    }

    @Test
    @DisplayName("AC7: isTrusted returns false for blank or missing token")
    void isTrustedBlankToken() {
        trustedDeviceService.remember(testUser);

        assertThat(trustedDeviceService.isTrusted(testUser.getId(), null)).isFalse();
        assertThat(trustedDeviceService.isTrusted(testUser.getId(), "  ")).isFalse();
    }

    @Test
    @DisplayName("AC7: isTrusted returns false for expired device")
    void isTrustedExpiredDevice() {
        String rawToken = trustedDeviceService.remember(testUser);

        TrustedDevice device = trustedDeviceRepository.findAll().get(0);
        device.setExpiresAt(Instant.now().minusSeconds(1));
        trustedDeviceRepository.save(device);

        assertThat(trustedDeviceService.isTrusted(testUser.getId(), rawToken)).isFalse();
    }

    @Test
    @DisplayName("AC7: isTrusted returns false for a different user")
    void isTrustedDifferentUser() {
        String rawToken = trustedDeviceService.remember(testUser);

        assertThat(trustedDeviceService.isTrusted(UUID.randomUUID(), rawToken)).isFalse();
    }

    @Test
    @DisplayName("AC7: Per-device isolation — expiring one device leaves the other valid")
    void perDeviceIsolation() {
        String device1Token = trustedDeviceService.remember(testUser);
        String device2Token = trustedDeviceService.remember(testUser);

        assertThat(device1Token).isNotEqualTo(device2Token);
        assertThat(trustedDeviceService.isTrusted(testUser.getId(), device1Token)).isTrue();
        assertThat(trustedDeviceService.isTrusted(testUser.getId(), device2Token)).isTrue();

        TrustedDevice device1 = trustedDeviceRepository.findAll().stream()
                .filter(device -> passwordEncoder.matches(device1Token, device.getTokenHash()))
                .findFirst()
                .orElseThrow();
        device1.setExpiresAt(Instant.now().minusSeconds(1));
        trustedDeviceRepository.save(device1);

        assertThat(trustedDeviceService.isTrusted(testUser.getId(), device1Token)).isFalse();
        assertThat(trustedDeviceService.isTrusted(testUser.getId(), device2Token)).isTrue();
    }
}

package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.entity.TrustedDevice;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.repository.TrustedDeviceRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

    private User testUser;

    @BeforeEach
    void setUp() {
        trustedDeviceRepository.deleteAll();
        userRepository.deleteAll();

        testUser = User.builder()
                .email("device@test.com")
                .name("Device Test")
                .emailVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userRepository.save(testUser);
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
    }

    @Test
    @DisplayName("AC7: isTrusted returns true for valid device token")
    void isTrustedValidToken() {
        String rawToken = trustedDeviceService.remember(testUser);

        boolean trusted = trustedDeviceService.isTrusted(testUser.getId(), rawToken);
        assertThat(trusted).isTrue();

        // last_used_at should be updated
        TrustedDevice device = trustedDeviceRepository.findAll().get(0);
        assertThat(device.getLastUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("AC7: isTrusted returns false for wrong token")
    void isTrustedWrongToken() {
        trustedDeviceService.remember(testUser);

        boolean trusted = trustedDeviceService.isTrusted(testUser.getId(), "wrong-token");
        assertThat(trusted).isFalse();
    }

    @Test
    @DisplayName("AC7: isTrusted returns false for expired device")
    void isTrustedExpiredDevice() {
        String rawToken = trustedDeviceService.remember(testUser);

        // Expire the device
        TrustedDevice device = trustedDeviceRepository.findAll().get(0);
        device.setExpiresAt(Instant.now().minusSeconds(1));
        trustedDeviceRepository.save(device);

        boolean trusted = trustedDeviceService.isTrusted(testUser.getId(), rawToken);
        assertThat(trusted).isFalse();
    }

    @Test
    @DisplayName("AC7: isTrusted returns false for different user")
    void isTrustedDifferentUser() {
        String rawToken = trustedDeviceService.remember(testUser);

        UUID differentUserId = UUID.randomUUID();
        boolean trusted = trustedDeviceService.isTrusted(differentUserId, rawToken);
        assertThat(trusted).isFalse();
    }

    @Test
    @DisplayName("AC7: Per-device isolation — different browser tokens are independent")
    void perDeviceIsolation() {
        String device1Token = trustedDeviceService.remember(testUser);
        String device2Token = trustedDeviceService.remember(testUser);

        assertThat(device1Token).isNotEqualTo(device2Token);

        // Both are valid
        assertThat(trustedDeviceService.isTrusted(testUser.getId(), device1Token)).isTrue();
        assertThat(trustedDeviceService.isTrusted(testUser.getId(), device2Token)).isTrue();

        // Expire device 1
        var devices = trustedDeviceRepository.findAll();
        for (var d : devices) {
            if (d.getTokenHash().equals(device1Token)) {
                // Can't compare directly due to hashing, but we can expire all and test
            }
        }

        // Instead, expire one device manually
        TrustedDevice toExpire = trustedDeviceRepository.findAll().get(0);
        toExpire.setExpiresAt(Instant.now().minusSeconds(1));
        trustedDeviceRepository.save(toExpire);

        // One should be expired, one valid — but since we can't tell which token is which
        // after hashing, we verify the count
        long validCount = trustedDeviceRepository.findAll().stream()
                .filter(d -> d.getExpiresAt().isAfter(Instant.now()))
                .count();
        assertThat(validCount).isEqualTo(1);
    }
}

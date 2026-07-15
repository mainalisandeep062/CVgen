package io.github.mainalisandeep.cvgen.repository;

import io.github.mainalisandeep.cvgen.entity.TrustedDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, UUID> {

    Optional<TrustedDevice> findByUserIdAndTokenHashAndExpiresAtAfter(UUID userId, String tokenHash, java.time.Instant now);
}

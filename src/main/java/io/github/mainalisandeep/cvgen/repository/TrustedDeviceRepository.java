package io.github.mainalisandeep.cvgen.repository;

import io.github.mainalisandeep.cvgen.entity.TrustedDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, UUID> {

    /** Non-expired devices of one user - the only candidates worth hash-matching. */
    @Query("SELECT d FROM TrustedDevice d WHERE d.user.id = :userId AND d.expiresAt > :now")
    List<TrustedDevice> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}

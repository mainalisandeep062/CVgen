package io.github.mainalisandeep.cvgen.repository;

import io.github.mainalisandeep.cvgen.entity.Cv;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Every read is scoped by owner in the query itself.
 * <p>
 * There is deliberately no {@code findById} usage above this interface: loading first and
 * comparing the owner afterwards is the pattern that eventually gets refactored into a leak.
 * A CV belonging to someone else must be indistinguishable from one that does not exist.
 */
@Repository
public interface CvRepository extends JpaRepository<Cv, UUID> {

    Optional<Cv> findByIdAndUserId(UUID id, UUID userId);

    Page<Cv> findAllByUserId(UUID userId, Pageable pageable);

    long countByUserId(UUID userId);
}

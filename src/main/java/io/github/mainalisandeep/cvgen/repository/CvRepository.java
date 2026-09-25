package io.github.mainalisandeep.cvgen.repository;

import io.github.mainalisandeep.cvgen.entity.Cv;
import io.github.mainalisandeep.cvgen.enums.CvStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every read is scoped by owner in the query itself.
 * <p>
 * There is deliberately no {@code findById} usage above this interface: loading first and
 * comparing the owner afterwards is the pattern that eventually gets refactored into a leak.
 * A CV belonging to someone else must be indistinguishable from one that does not exist.
 * <p>
 * The unscoped aggregates at the bottom serve the admin module only and never return a document.
 */
@Repository
public interface CvRepository extends JpaRepository<Cv, UUID> {

    Optional<Cv> findByIdAndUserId(UUID id, UUID userId);

    Page<Cv> findAllByUserId(UUID userId, Pageable pageable);

    long countByUserId(UUID userId);

    long countByTemplateKey(String templateKey);

    @Query("SELECT c.user.id AS userId, COUNT(c) AS count FROM Cv c WHERE c.user.id IN :userIds GROUP BY c.user.id")
    List<UserCvCount> countByUserIds(@Param("userIds") Collection<UUID> userIds);

    @Query("SELECT c.templateKey AS templateKey, COUNT(c) AS count FROM Cv c GROUP BY c.templateKey")
    List<TemplateCvCount> countByTemplateKeys();

    @Query("SELECT c.status AS status, COUNT(c) AS count FROM Cv c GROUP BY c.status")
    List<StatusCvCount> countByStatus();

    /** Newest CVs across all users, owner fetched, for the activity feed. */
    @EntityGraph(attributePaths = "user")
    List<Cv> findAllBy(Pageable pageable);

    interface UserCvCount {
        UUID getUserId();

        long getCount();
    }

    interface TemplateCvCount {
        String getTemplateKey();

        long getCount();
    }

    interface StatusCvCount {
        CvStatus getStatus();

        long getCount();
    }
}

package io.github.mainalisandeep.cvgen.repository;

import io.github.mainalisandeep.cvgen.entity.TemplateUnlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.UUID;

@Repository
public interface TemplateUnlockRepository extends JpaRepository<TemplateUnlock, UUID> {

    boolean existsByUserIdAndTemplateId(UUID userId, UUID templateId);

    @Query("SELECT u.template.id FROM TemplateUnlock u WHERE u.user.id = :userId")
    Set<UUID> findTemplateIdsByUserId(@Param("userId") UUID userId);
}

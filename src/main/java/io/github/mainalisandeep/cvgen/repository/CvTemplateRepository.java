package io.github.mainalisandeep.cvgen.repository;

import io.github.mainalisandeep.cvgen.entity.CvTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CvTemplateRepository extends JpaRepository<CvTemplate, UUID> {

    boolean existsByTemplateKey(String templateKey);

    Optional<CvTemplate> findByTemplateKeyAndActiveTrue(String templateKey);

    /** Picker order. */
    List<CvTemplate> findAllByActiveTrueOrderBySortOrderAscNameAsc();

    List<CvTemplate> findAllByOrderBySortOrderAscNameAsc();
}

package io.github.mainalisandeep.cvgen.repository;

import io.github.mainalisandeep.cvgen.entity.OtpCode;
import io.github.mainalisandeep.cvgen.enums.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

    Optional<OtpCode> findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(String email, OtpPurpose purpose);
}

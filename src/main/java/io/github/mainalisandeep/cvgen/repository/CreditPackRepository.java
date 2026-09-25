package io.github.mainalisandeep.cvgen.repository;

import io.github.mainalisandeep.cvgen.entity.CreditPack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CreditPackRepository extends JpaRepository<CreditPack, UUID> {

    List<CreditPack> findAllByOrderBySortOrderAscNameAsc();

    List<CreditPack> findAllByActiveTrueOrderBySortOrderAscNameAsc();
}

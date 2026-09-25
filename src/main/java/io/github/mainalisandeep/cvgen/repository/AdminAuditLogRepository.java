package io.github.mainalisandeep.cvgen.repository;

import io.github.mainalisandeep.cvgen.entity.AdminAuditLog;
import io.github.mainalisandeep.cvgen.enums.AdminAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

    Page<AdminAuditLog> findAllByAction(AdminAction action, Pageable pageable);

    /** Newest entries without a count query, for the activity feed. */
    List<AdminAuditLog> findAllBy(Pageable pageable);
}

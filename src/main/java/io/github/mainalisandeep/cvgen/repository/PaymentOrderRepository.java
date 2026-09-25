package io.github.mainalisandeep.cvgen.repository;

import io.github.mainalisandeep.cvgen.entity.PaymentOrder;
import io.github.mainalisandeep.cvgen.enums.PaymentGateway;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Scoped by owner like CVs: someone else's order is indistinguishable from one that does not exist. */
@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {

    Optional<PaymentOrder> findByIdAndUserId(UUID id, UUID userId);

    Optional<PaymentOrder> findByGatewayAndGatewayReferenceAndUserId(PaymentGateway gateway, String reference, UUID userId);

    /**
     * Locks the order for the state change. Two confirmations racing (a double-clicked return page,
     * the user refreshing it) serialise here, and the second sees COMPLETED and credits nothing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM PaymentOrder o WHERE o.id = :id")
    Optional<PaymentOrder> findByIdForUpdate(@Param("id") UUID id);
}

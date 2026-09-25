package io.github.mainalisandeep.cvgen.repository;

import io.github.mainalisandeep.cvgen.entity.CreditTransaction;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionStatus;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The credit ledger. Rows are written only by {@code CreditLedgerService}; everything here reads.
 * <p>
 * The billing aggregates are native SQL on purpose: {@code FILTER}, {@code generate_series} and
 * UTC day bucketing have no JPQL form, and the alternative is loading the ledger into memory.
 * Aliases are quoted so the projection getters match regardless of how the driver cases them.
 */
@Repository
public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, UUID>,
        JpaSpecificationExecutor<CreditTransaction> {

    /** The admin ledger. Owner and pack are fetched with the page, not one query per row. */
    @Override
    @EntityGraph(attributePaths = {"user", "pack"})
    Page<CreditTransaction> findAll(Specification<CreditTransaction> spec, Pageable pageable);

    /** One user's history without a count query; ordering comes from the pageable. */
    @EntityGraph(attributePaths = {"user", "pack"})
    List<CreditTransaction> findAllByUserId(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "pack"})
    List<CreditTransaction> findAllByTypeAndStatus(CreditTransactionType type, CreditTransactionStatus status, Pageable pageable);

    /** Serialises refunds of the same purchase, so it cannot be reversed twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM CreditTransaction t WHERE t.id = :id")
    Optional<CreditTransaction> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByPackId(UUID packId);

    @Query("""
            SELECT t.pack.id AS packId, COUNT(t) AS count FROM CreditTransaction t
            WHERE t.pack IS NOT NULL AND t.type = :type AND t.status = :status
            GROUP BY t.pack.id
            """)
    List<PackCount> countByPack(@Param("type") CreditTransactionType type, @Param("status") CreditTransactionStatus status);

    @Query(value = """
            SELECT
                CAST(COALESCE(SUM(amount_minor) FILTER (WHERE type = 'PURCHASE' AND status = 'COMPLETED'), 0) AS BIGINT) AS "revenueMinor",
                CAST(COALESCE(SUM(amount_minor) FILTER (WHERE type = 'REFUND'), 0) AS BIGINT) AS "refundsMinor",
                COUNT(*) FILTER (WHERE type = 'PURCHASE' AND status = 'COMPLETED') AS "purchases",
                CAST(COALESCE(SUM(credits) FILTER (WHERE type = 'PURCHASE' AND status = 'COMPLETED'), 0) AS BIGINT) AS "creditsSold",
                CAST(COALESCE(SUM(credits) FILTER (WHERE type = 'ADMIN_GRANT'), 0) AS BIGINT) AS "creditsGranted",
                CAST(COALESCE(-SUM(credits) FILTER (WHERE type = 'ADMIN_DEDUCT'), 0) AS BIGINT) AS "creditsDeducted",
                CAST(COALESCE(-SUM(credits) FILTER (WHERE type = 'SPEND'), 0) AS BIGINT) AS "creditsSpent"
            FROM credit_transactions
            WHERE created_at >= :from AND created_at < :to
            """, nativeQuery = true)
    BillingTotals billingTotals(@Param("from") Instant from, @Param("to") Instant to);

    /** One row per UTC day in [fromDay, toDay], zero-filled. */
    @Query(value = """
            SELECT to_char(days.day, 'YYYY-MM-DD') AS "date",
                   CAST(COALESCE(p.revenue, 0) AS BIGINT) AS "revenueMinor",
                   COALESCE(p.purchases, 0) AS "purchases"
            FROM (
                SELECT CAST(d AS DATE) AS day
                FROM generate_series(CAST(:fromDay AS DATE), CAST(:toDay AS DATE), INTERVAL '1 day') AS d
            ) days
            LEFT JOIN (
                SELECT CAST(created_at AT TIME ZONE 'UTC' AS DATE) AS day,
                       SUM(amount_minor) AS revenue,
                       COUNT(*) AS purchases
                FROM credit_transactions
                WHERE type = 'PURCHASE' AND status = 'COMPLETED'
                  AND created_at >= :from AND created_at < :to
                GROUP BY 1
            ) p ON p.day = days.day
            ORDER BY days.day
            """, nativeQuery = true)
    List<RevenueDay> revenueSeries(@Param("fromDay") LocalDate fromDay, @Param("toDay") LocalDate toDay,
                                   @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = """
            SELECT p.id AS "packId", p.name AS "name",
                   COUNT(t.id) AS "purchases",
                   CAST(COALESCE(SUM(t.amount_minor), 0) AS BIGINT) AS "revenueMinor"
            FROM credit_transactions t
            JOIN credit_packs p ON p.id = t.pack_id
            WHERE t.type = 'PURCHASE' AND t.status = 'COMPLETED'
              AND t.created_at >= :from AND t.created_at < :to
            GROUP BY p.id, p.name
            ORDER BY "revenueMinor" DESC, "purchases" DESC, p.name
            """, nativeQuery = true)
    List<PackRevenue> topPacks(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = """
            SELECT COALESCE(payment_method, 'unknown') AS "method",
                   COUNT(*) AS "purchases",
                   CAST(COALESCE(SUM(amount_minor), 0) AS BIGINT) AS "revenueMinor"
            FROM credit_transactions
            WHERE type = 'PURCHASE' AND status = 'COMPLETED'
              AND created_at >= :from AND created_at < :to
            GROUP BY 1
            ORDER BY "revenueMinor" DESC, "method"
            """, nativeQuery = true)
    List<MethodRevenue> paymentMethods(@Param("from") Instant from, @Param("to") Instant to);

    interface PackCount {
        UUID getPackId();

        long getCount();
    }

    interface BillingTotals {
        long getRevenueMinor();

        long getRefundsMinor();

        long getPurchases();

        long getCreditsSold();

        long getCreditsGranted();

        long getCreditsDeducted();

        long getCreditsSpent();
    }

    interface RevenueDay {
        String getDate();

        long getRevenueMinor();

        long getPurchases();
    }

    interface PackRevenue {
        UUID getPackId();

        String getName();

        long getPurchases();

        long getRevenueMinor();
    }

    interface MethodRevenue {
        String getMethod();

        long getPurchases();

        long getRevenueMinor();
    }
}

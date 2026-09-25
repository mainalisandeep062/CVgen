package io.github.mainalisandeep.cvgen.repository;

import io.github.mainalisandeep.cvgen.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Cross-table aggregates for the admin dashboard.
 * <p>
 * Extends the bare {@link org.springframework.data.repository.Repository} marker, so it exposes no
 * CRUD; {@link User} is only the domain type Spring Data requires. Every query returns counts and
 * sums computed in PostgreSQL - nothing here loads rows to count them in memory. Days are bucketed
 * in UTC, and aliases are quoted so projection getters match exactly.
 */
@Repository
public interface AdminAnalyticsRepository extends org.springframework.data.repository.Repository<User, java.util.UUID> {

    @Query(value = """
            SELECT
                (SELECT COUNT(*) FROM users) AS "users",
                (SELECT COUNT(*) FROM users WHERE status = 'ACTIVE') AS "activeUsers",
                (SELECT COUNT(*) FROM users WHERE status = 'SUSPENDED') AS "suspendedUsers",
                (SELECT COUNT(*) FROM users WHERE role = 'ADMIN') AS "admins",
                (SELECT COUNT(*) FROM users WHERE email_verified) AS "verifiedUsers",
                (SELECT COUNT(*) FROM cvs) AS "cvs",
                (SELECT COUNT(*) FROM cvs WHERE status = 'READY') AS "readyCvs",
                (SELECT COUNT(*) FROM cv_templates) AS "templates",
                (SELECT COUNT(*) FROM cv_templates WHERE active) AS "activeTemplates",
                (SELECT CAST(COALESCE(SUM(amount_minor), 0) AS BIGINT) FROM credit_transactions
                    WHERE type = 'PURCHASE' AND status = 'COMPLETED') AS "revenueMinor",
                (SELECT CAST(COALESCE(SUM(credit_balance), 0) AS BIGINT) FROM users) AS "creditsInCirculation"
            """, nativeQuery = true)
    Totals totals();

    /** Counts inside the half-open window [from, to). */
    @Query(value = """
            SELECT
                (SELECT COUNT(*) FROM users WHERE created_at >= :from AND created_at < :to) AS "newUsers",
                (SELECT COUNT(*) FROM cvs WHERE created_at >= :from AND created_at < :to) AS "newCvs",
                (SELECT CAST(COALESCE(SUM(amount_minor), 0) AS BIGINT) FROM credit_transactions
                    WHERE type = 'PURCHASE' AND status = 'COMPLETED'
                      AND created_at >= :from AND created_at < :to) AS "revenueMinor",
                (SELECT COUNT(*) FROM credit_transactions
                    WHERE type = 'PURCHASE' AND status = 'COMPLETED'
                      AND created_at >= :from AND created_at < :to) AS "purchases",
                (SELECT COUNT(*) FROM users WHERE last_login_at >= :from AND last_login_at < :to) AS "activeUsers"
            """, nativeQuery = true)
    Period period(@Param("from") Instant from, @Param("to") Instant to);

    /** One row per UTC day in [fromDay, toDay], zero-filled by the day spine. */
    @Query(value = """
            SELECT to_char(days.day, 'YYYY-MM-DD') AS "date",
                   COALESCE(s.signups, 0) AS "signups",
                   COALESCE(c.cvs, 0) AS "cvs",
                   CAST(COALESCE(r.revenue, 0) AS BIGINT) AS "revenueMinor"
            FROM (
                SELECT CAST(d AS DATE) AS day
                FROM generate_series(CAST(:fromDay AS DATE), CAST(:toDay AS DATE), INTERVAL '1 day') AS d
            ) days
            LEFT JOIN (
                SELECT CAST(created_at AT TIME ZONE 'UTC' AS DATE) AS day, COUNT(*) AS signups
                FROM users WHERE created_at >= :from AND created_at < :to
                GROUP BY 1
            ) s ON s.day = days.day
            LEFT JOIN (
                SELECT CAST(created_at AT TIME ZONE 'UTC' AS DATE) AS day, COUNT(*) AS cvs
                FROM cvs WHERE created_at >= :from AND created_at < :to
                GROUP BY 1
            ) c ON c.day = days.day
            LEFT JOIN (
                SELECT CAST(created_at AT TIME ZONE 'UTC' AS DATE) AS day, SUM(amount_minor) AS revenue
                FROM credit_transactions
                WHERE type = 'PURCHASE' AND status = 'COMPLETED'
                  AND created_at >= :from AND created_at < :to
                GROUP BY 1
            ) r ON r.day = days.day
            ORDER BY days.day
            """, nativeQuery = true)
    List<Day> series(@Param("fromDay") LocalDate fromDay, @Param("toDay") LocalDate toDay,
                     @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = """
            SELECT t.template_key AS "key", t.name AS "name", COUNT(c.id) AS "cvCount"
            FROM cv_templates t
            LEFT JOIN cvs c ON c.template_key = t.template_key
            GROUP BY t.id, t.template_key, t.name, t.sort_order
            ORDER BY COUNT(c.id) DESC, t.sort_order, t.name
            """, nativeQuery = true)
    List<TemplateUsage> templateUsage();

    /**
     * "local" counts accounts that can sign in with a password; each provider counts distinct
     * linked accounts. One account can appear under several, so the rows do not sum to the user total.
     */
    @Query(value = """
            SELECT provider AS "provider", users AS "users" FROM (
                SELECT 'local' AS provider, COUNT(*) AS users, 0 AS rank
                FROM users WHERE password_hash IS NOT NULL AND password_hash <> ''
                UNION ALL
                SELECT provider, COUNT(DISTINCT user_id), 1
                FROM user_identities GROUP BY provider
            ) breakdown
            ORDER BY rank, provider
            """, nativeQuery = true)
    List<ProviderCount> providerBreakdown();

    interface Totals {
        long getUsers();

        long getActiveUsers();

        long getSuspendedUsers();

        long getAdmins();

        long getVerifiedUsers();

        long getCvs();

        long getReadyCvs();

        long getTemplates();

        long getActiveTemplates();

        long getRevenueMinor();

        long getCreditsInCirculation();
    }

    interface Period {
        long getNewUsers();

        long getNewCvs();

        long getRevenueMinor();

        long getPurchases();

        long getActiveUsers();
    }

    interface Day {
        String getDate();

        long getSignups();

        long getCvs();

        long getRevenueMinor();
    }

    interface TemplateUsage {
        String getKey();

        String getName();

        long getCvCount();
    }

    interface ProviderCount {
        String getProvider();

        long getUsers();
    }
}

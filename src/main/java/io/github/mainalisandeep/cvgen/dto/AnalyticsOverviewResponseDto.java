package io.github.mainalisandeep.cvgen.dto;

import io.github.mainalisandeep.cvgen.enums.ActivityType;
import io.github.mainalisandeep.cvgen.enums.CvStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The admin dashboard. {@code totals} are all-time; {@code period} and {@code series} cover the last
 * {@code days} UTC days, today included.
 *
 * @param series         exactly {@code days} entries, oldest first, zero-filled
 * @param recentActivity at most 12, newest first
 */
public record AnalyticsOverviewResponseDto(
        int days,
        String currency,
        Totals totals,
        Period period,
        List<SeriesPoint> series,
        List<TemplateUsage> templateUsage,
        List<ProviderCount> providerBreakdown,
        List<CvStatusCount> cvStatus,
        List<ActivityItem> recentActivity
) {

    /** @param creditsInCirculation sum of every user's balance */
    public record Totals(
            long users,
            long activeUsers,
            long suspendedUsers,
            long admins,
            long verifiedUsers,
            long cvs,
            long readyCvs,
            long templates,
            long activeTemplates,
            long revenueMinor,
            long creditsInCirculation
    ) {
    }

    /**
     * @param activeUsers distinct users who signed in inside the window
     * @param previousNewUsers and the other {@code previous*} values cover the same-length window
     *                         immediately before
     */
    public record Period(
            long newUsers,
            long newCvs,
            long revenueMinor,
            long purchases,
            long activeUsers,
            long previousNewUsers,
            long previousNewCvs,
            long previousRevenueMinor,
            long previousPurchases
    ) {
    }

    public record SeriesPoint(LocalDate date, long signups, long cvs, long revenueMinor) {
    }

    public record TemplateUsage(String key, String name, long cvCount) {
    }

    /** @param provider {@code local} for password accounts, otherwise the OAuth2 provider id */
    public record ProviderCount(String provider, long users) {
    }

    public record CvStatusCount(CvStatus status, long count) {
    }

    public record ActivityItem(ActivityType type, String title, String subtitle, LocalDateTime at) {
    }
}

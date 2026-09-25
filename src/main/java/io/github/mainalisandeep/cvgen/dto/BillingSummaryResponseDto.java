package io.github.mainalisandeep.cvgen.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Money and credit flow over the last {@code days} UTC days, today included.
 *
 * @param revenueMinor      completed purchases; a refunded purchase no longer counts
 * @param refundsMinor      amounts of REFUND rows in the window, reported separately
 * @param averageOrderMinor revenue / purchases, integer division, 0 without purchases
 * @param creditsDeducted   positive number
 * @param creditsSpent      positive number
 * @param series            exactly {@code days} entries, oldest first, zero-filled
 */
public record BillingSummaryResponseDto(
        int days,
        String currency,
        long revenueMinor,
        long refundsMinor,
        long purchases,
        long averageOrderMinor,
        long creditsSold,
        long creditsGranted,
        long creditsDeducted,
        long creditsSpent,
        List<SeriesPoint> series,
        List<TopPack> topPacks,
        List<PaymentMethodStat> paymentMethods
) {

    public record SeriesPoint(LocalDate date, long revenueMinor, long purchases) {
    }

    public record TopPack(UUID packId, String name, long purchases, long revenueMinor) {
    }

    /** @param method payment method, {@code "unknown"} when a purchase recorded none */
    public record PaymentMethodStat(String method, long purchases, long revenueMinor) {
    }
}

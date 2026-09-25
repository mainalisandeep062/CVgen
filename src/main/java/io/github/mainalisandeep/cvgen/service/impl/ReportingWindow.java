package io.github.mainalisandeep.cvgen.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * The last {@code days} UTC calendar days, today included.
 * <p>
 * Carried twice on purpose: as dates, for the {@code generate_series} day spine, and as a half-open
 * instant range [{@code from}, {@code to}) for filtering, so a row at 23:59:59.999 UTC lands in exactly
 * one bucket. {@code previousFrom} starts the same-length window immediately before, which ends at
 * {@code from}.
 */
record ReportingWindow(LocalDate firstDay, LocalDate lastDay, Instant from, Instant to, Instant previousFrom) {

    static ReportingWindow lastDays(int days) {
        LocalDate lastDay = LocalDate.now(ZoneOffset.UTC);
        LocalDate firstDay = lastDay.minusDays(days - 1L);
        return new ReportingWindow(
                firstDay,
                lastDay,
                startOf(firstDay),
                startOf(lastDay.plusDays(1)),
                startOf(firstDay.minusDays(days))
        );
    }

    private static Instant startOf(LocalDate day) {
        return day.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}

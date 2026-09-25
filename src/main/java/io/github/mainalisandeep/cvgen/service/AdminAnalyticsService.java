package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.dto.AnalyticsOverviewResponseDto;

/** The admin dashboard. */
public interface AdminAnalyticsService {

    /** @param days window length in UTC days, today included */
    AnalyticsOverviewResponseDto overview(int days);
}

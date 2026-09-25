package io.github.mainalisandeep.cvgen.service.impl;

import io.github.mainalisandeep.cvgen.common.message.ActivityMessageConstant;
import io.github.mainalisandeep.cvgen.common.message.CustomMessageSource;
import io.github.mainalisandeep.cvgen.dto.AnalyticsOverviewResponseDto;
import io.github.mainalisandeep.cvgen.dto.AnalyticsOverviewResponseDto.ActivityItem;
import io.github.mainalisandeep.cvgen.entity.CreditPack;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.enums.ActivityType;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionStatus;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionType;
import io.github.mainalisandeep.cvgen.enums.CvStatus;
import io.github.mainalisandeep.cvgen.repository.AdminAnalyticsRepository;
import io.github.mainalisandeep.cvgen.repository.AdminAuditLogRepository;
import io.github.mainalisandeep.cvgen.repository.CreditTransactionRepository;
import io.github.mainalisandeep.cvgen.repository.CvRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.service.AdminAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AdminAnalyticsServiceImpl implements AdminAnalyticsService {

    private static final int RECENT_ACTIVITY_LIMIT = 12;

    private final AdminAnalyticsRepository adminAnalyticsRepository;
    private final UserRepository userRepository;
    private final CvRepository cvRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final CustomMessageSource customMessageSource;

    @Override
    @Transactional(readOnly = true)
    public AnalyticsOverviewResponseDto overview(int days) {
        ReportingWindow window = ReportingWindow.lastDays(days);
        AdminAnalyticsRepository.Totals totals = adminAnalyticsRepository.totals();
        AdminAnalyticsRepository.Period current = adminAnalyticsRepository.period(window.from(), window.to());
        AdminAnalyticsRepository.Period previous = adminAnalyticsRepository.period(window.previousFrom(), window.from());

        return new AnalyticsOverviewResponseDto(
                days,
                CreditPack.DEFAULT_CURRENCY,
                new AnalyticsOverviewResponseDto.Totals(
                        totals.getUsers(), totals.getActiveUsers(), totals.getSuspendedUsers(), totals.getAdmins(),
                        totals.getVerifiedUsers(), totals.getCvs(), totals.getReadyCvs(), totals.getTemplates(),
                        totals.getActiveTemplates(), totals.getRevenueMinor(), totals.getCreditsInCirculation()),
                new AnalyticsOverviewResponseDto.Period(
                        current.getNewUsers(), current.getNewCvs(), current.getRevenueMinor(), current.getPurchases(),
                        current.getActiveUsers(), previous.getNewUsers(), previous.getNewCvs(),
                        previous.getRevenueMinor(), previous.getPurchases()),
                adminAnalyticsRepository.series(window.firstDay(), window.lastDay(), window.from(), window.to()).stream()
                        .map(day -> new AnalyticsOverviewResponseDto.SeriesPoint(
                                LocalDate.parse(day.getDate()), day.getSignups(), day.getCvs(), day.getRevenueMinor()))
                        .toList(),
                adminAnalyticsRepository.templateUsage().stream()
                        .map(usage -> new AnalyticsOverviewResponseDto.TemplateUsage(usage.getKey(), usage.getName(), usage.getCvCount()))
                        .toList(),
                adminAnalyticsRepository.providerBreakdown().stream()
                        .map(provider -> new AnalyticsOverviewResponseDto.ProviderCount(provider.getProvider(), provider.getUsers()))
                        .toList(),
                cvStatusCounts(),
                recentActivity()
        );
    }

    /** Every status appears, zero included, so the chart never loses a slice. */
    private List<AnalyticsOverviewResponseDto.CvStatusCount> cvStatusCounts() {
        Map<CvStatus, Long> counts = cvRepository.countByStatus().stream()
                .collect(Collectors.toMap(CvRepository.StatusCvCount::getStatus, CvRepository.StatusCvCount::getCount));
        return Arrays.stream(CvStatus.values())
                .map(status -> new AnalyticsOverviewResponseDto.CvStatusCount(status, counts.getOrDefault(status, 0L)))
                .toList();
    }

    /**
     * The newest entries of four feeds merged in memory. Each source is capped at the final limit in SQL,
     * which is enough: the merged top twelve can never need a thirteenth row from any one source.
     */
    private List<ActivityItem> recentActivity() {
        Pageable newest = PageRequest.of(0, RECENT_ACTIVITY_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt"));

        Stream<ActivityItem> signups = userRepository.findAllBy(newest).stream()
                .map(user -> new ActivityItem(ActivityType.USER_SIGNUP,
                        customMessageSource.get(ActivityMessageConstant.ACTIVITY_USER_SIGNUP, displayName(user)),
                        user.getEmail(), toLocalDateTime(user.getCreatedAt())));

        Stream<ActivityItem> cvs = cvRepository.findAllBy(newest).stream()
                .map(cv -> new ActivityItem(ActivityType.CV_CREATED,
                        customMessageSource.get(ActivityMessageConstant.ACTIVITY_CV_CREATED, displayName(cv.getUser())),
                        cv.getTitle(), toLocalDateTime(cv.getCreatedAt())));

        Stream<ActivityItem> purchases = creditTransactionRepository
                .findAllByTypeAndStatus(CreditTransactionType.PURCHASE, CreditTransactionStatus.COMPLETED, newest).stream()
                .map(purchase -> new ActivityItem(ActivityType.PURCHASE,
                        customMessageSource.get(ActivityMessageConstant.ACTIVITY_PURCHASE, displayName(purchase.getUser()), purchase.getCredits()),
                        purchase.getPack() == null ? purchase.getUser().getEmail() : purchase.getPack().getName(),
                        toLocalDateTime(purchase.getCreatedAt())));

        Stream<ActivityItem> adminActions = adminAuditLogRepository.findAllBy(newest).stream()
                .map(entry -> new ActivityItem(ActivityType.ADMIN_ACTION, entry.getSummary(), entry.getActorEmail(),
                        toLocalDateTime(entry.getCreatedAt())));

        return Stream.of(signups, cvs, purchases, adminActions)
                .flatMap(stream -> stream)
                .sorted(Comparator.comparing(ActivityItem::at).reversed())
                .limit(RECENT_ACTIVITY_LIMIT)
                .toList();
    }

    private String displayName(User user) {
        return user.getName() == null || user.getName().isBlank() ? user.getEmail() : user.getName();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}

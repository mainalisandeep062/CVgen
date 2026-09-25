package io.github.mainalisandeep.cvgen.controller;

import io.github.mainalisandeep.cvgen.config.CvProperties;
import io.github.mainalisandeep.cvgen.entity.Cv;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.enums.CvStatus;
import io.github.mainalisandeep.cvgen.repository.CvRepository;
import io.github.mainalisandeep.cvgen.support.AdminTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The dashboard payload.
 *
 * <p>Counts are asserted as lower bounds: the aggregates run over the whole database, and this test
 * only controls the rows it creates itself.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AdminAnalyticsControllerTest extends AdminTestSupport {

    @Autowired
    private CvRepository cvRepository;

    @Autowired
    private CvProperties cvProperties;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = saveAdmin("analytics-admin");
        User member = saveUser("analytics-member");
        cvRepository.save(Cv.builder()
                .user(member)
                .title("Analytics CV")
                .templateKey(cvProperties.getDefaultTemplateKey())
                .locale(cvProperties.getDefaultLocale())
                .status(CvStatus.DRAFT)
                .content(objectMapper.createObjectNode().put("schemaVersion", 1))
                .build());
    }

    @Test
    @DisplayName("The overview carries totals, the window, a zero-filled series and recent activity")
    void overviewShape() throws Exception {
        mockMvc.perform(get("/api/admin/analytics/overview").param("days", "30").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(30))
                .andExpect(jsonPath("$.data.currency").value("NPR"))
                .andExpect(jsonPath("$.data.totals.users").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.totals.admins").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.totals.cvs").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.totals.templates").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.totals.revenueMinor").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.period.newUsers").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.period.previousNewUsers").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.series.length()").value(30))
                .andExpect(jsonPath("$.data.series[29].date")
                        .value(LocalDate.now(ZoneOffset.UTC).toString()))
                .andExpect(jsonPath("$.data.series[29].signups").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.templateUsage[0].key").value(cvProperties.getDefaultTemplateKey()))
                .andExpect(jsonPath("$.data.cvStatus.length()").value(CvStatus.values().length))
                .andExpect(jsonPath("$.data.providerBreakdown[0].provider").value("local"))
                .andExpect(jsonPath("$.data.recentActivity.length()").value(lessThanOrEqualTo(12)))
                // Newest first, and the CV was created after both accounts.
                .andExpect(jsonPath("$.data.recentActivity[0].type").value("CV_CREATED"))
                .andExpect(jsonPath("$.data.recentActivity[0].subtitle").value("Analytics CV"))
                .andExpect(jsonPath("$.data.recentActivity[?(@.type=='USER_SIGNUP')]").isNotEmpty());
    }

    @Test
    @DisplayName("The series has exactly one entry per requested day")
    void seriesLengthMatchesWindow() throws Exception {
        mockMvc.perform(get("/api/admin/analytics/overview").param("days", "7").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(7))
                .andExpect(jsonPath("$.data.series.length()").value(7))
                .andExpect(jsonPath("$.data.series[0].date")
                        .value(LocalDate.now(ZoneOffset.UTC).minusDays(6).toString()));
    }

    @Test
    @DisplayName("Omitting days falls back to 30")
    void defaultWindowIsThirtyDays() throws Exception {
        mockMvc.perform(get("/api/admin/analytics/overview").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(30))
                .andExpect(jsonPath("$.data.series.length()").value(30));
    }

    @ParameterizedTest(name = "days={0} is rejected")
    @ValueSource(strings = {"3", "0", "-1", "366", "1000"})
    @DisplayName("A window outside 7..365 days is rejected")
    void outOfRangeWindowIsRejected(String days) throws Exception {
        mockMvc.perform(get("/api/admin/analytics/overview").param("days", days).with(as(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(false));
    }
}

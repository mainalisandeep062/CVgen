package io.github.mainalisandeep.cvgen.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mainalisandeep.cvgen.config.CvProperties;
import io.github.mainalisandeep.cvgen.dto.CvAnalysisRequestDto;
import io.github.mainalisandeep.cvgen.entity.Cv;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.repository.CvRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.security.UserPrincipal;
import io.github.mainalisandeep.cvgen.support.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/cvs/{id}/analysis: the heuristic's promises, each pinned by one posting.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class CvAnalysisControllerTest extends PostgresContainerSupport {

    private static final String POSTING = """
            Senior Java Developer

            Requirements:
            - 3+ years of experience building services with Java and Spring Boot.
            - Solid PostgreSQL and REST API design.
            - Docker and Kubernetes in production.
            - Strong communication skills.

            Nice to have:
            - Kafka
            - AWS
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CvRepository cvRepository;

    @Autowired
    private CvProperties cvProperties;

    private User owner;
    private User intruder;

    @BeforeEach
    void setUp() {
        cvRepository.deleteAll();

        owner = userRepository.save(User.builder()
                .email("analysis-owner-" + UUID.randomUUID() + "@test.com")
                .name("Owner")
                .emailVerified(true)
                .build());

        intruder = userRepository.save(User.builder()
                .email("analysis-intruder-" + UUID.randomUUID() + "@test.com")
                .name("Intruder")
                .emailVerified(true)
                .build());
    }

    @Test
    @DisplayName("Matched, missing and optional terms come out in posting order with honest coverage")
    void splitsTermsAndScoresCoverage() throws Exception {
        Cv cv = persistCv(owner, filledDocument());

        analyze(owner, cv, "Senior Java Developer", POSTING)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched", contains("Java", "Spring Boot", "PostgreSQL", "REST", "Docker")))
                .andExpect(jsonPath("$.data.missing", contains("Kubernetes", "Communication")))
                .andExpect(jsonPath("$.data.optional", contains("Kafka", "AWS")))
                // 5 of 7 required terms; optional ones are not held against the CV
                .andExpect(jsonPath("$.data.coverage").value(71))
                // 5 of 6 hard skills - the soft skill does not drag the skills bar
                .andExpect(jsonPath("$.data.categories.skills").value(83))
                .andExpect(jsonPath("$.data.requiredYears").value(3))
                .andExpect(jsonPath("$.data.counts.matched").value(5))
                .andExpect(jsonPath("$.data.counts.missing").value(2));
    }

    @Test
    @DisplayName("Java in the posting is not satisfied by JavaScript in the CV")
    void wordBoundariesHold() throws Exception {
        ObjectNode document = document("Frontend Developer");
        skills(document, "JavaScript", "React");
        Cv cv = persistCv(owner, document);

        analyze(owner, cv, null, "We need Java and React experience.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched", contains("React")))
                .andExpect(jsonPath("$.data.missing", contains("Java")));
    }

    @Test
    @DisplayName("Overlapping experience is counted once toward years")
    void mergesOverlappingExperience() throws Exception {
        ObjectNode document = document("Backend Engineer");
        ArrayNode jobs = section(document, "EXPERIENCE");
        job(jobs, "2020-01", "2021-12", "Built APIs");
        job(jobs, "2021-01", "2022-12", "Built more APIs");
        Cv cv = persistCv(owner, document);

        analyze(owner, cv, null, "Java developer with 6 years of experience.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cvYears").value(3.0))
                .andExpect(jsonPath("$.data.categories.experience").value(50));
    }

    @Test
    @DisplayName("Structural gaps are reported, and a headline that ignores the target role is flagged")
    void reportsStructuralChecks() throws Exception {
        Cv cv = persistCv(owner, document("Graphic Designer"));

        analyze(owner, cv, "Senior React Developer", "React and TypeScript.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warnings[*].code", hasItem("MISSING_SUMMARY")))
                .andExpect(jsonPath("$.data.warnings[*].code", hasItem("MISSING_SKILLS")))
                .andExpect(jsonPath("$.data.warnings[*].code", hasItem("MISSING_EXPERIENCE")))
                .andExpect(jsonPath("$.data.warnings[*].code", hasItem("HEADLINE_MISMATCH")))
                .andExpect(jsonPath("$.data.warnings[?(@.code=='SINGLE_COLUMN')].passed", contains(true)))
                .andExpect(jsonPath("$.data.suggestions[0]").value("Use \"Senior React Developer\" or a close variant in your headline"));
    }

    @Test
    @DisplayName("A filled CV passes the checks it satisfies")
    void filledCvPassesChecks() throws Exception {
        Cv cv = persistCv(owner, filledDocument());

        analyze(owner, cv, "Senior Java Developer", POSTING)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warnings[*].code", not(hasItem("MISSING_SUMMARY"))))
                .andExpect(jsonPath("$.data.warnings[*].code", not(hasItem("HEADLINE_MISMATCH"))))
                .andExpect(jsonPath("$.data.warnings[*].code", not(hasItem("NO_METRICS"))))
                .andExpect(jsonPath("$.data.counts.warnings").value(0));
    }

    @Test
    @DisplayName("A blank job description is a validation error")
    void blankPostingIsRejected() throws Exception {
        Cv cv = persistCv(owner, filledDocument());

        analyze(owner, cv, null, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(false));
    }

    @Test
    @DisplayName("Analyzing someone else's CV is 404, not 403")
    void foreignCvIsNotFound() throws Exception {
        Cv cv = persistCv(owner, filledDocument());

        analyze(intruder, cv, null, POSTING)
                .andExpect(status().isNotFound());
    }

    private ResultActions analyze(User user, Cv cv, String jobTitle, String posting) throws Exception {
        var request = CvAnalysisRequestDto.builder().jobTitle(jobTitle).jobDescription(posting).build();
        return mockMvc.perform(post("/api/cvs/{id}/analysis", cv.getId())
                .with(asUser(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private ObjectNode filledDocument() {
        ObjectNode document = document("Senior Java Developer");
        section(document, "SUMMARY").addObject().put("text", "Backend engineer shipping payment systems.");
        ArrayNode jobs = section(document, "EXPERIENCE");
        job(jobs, "2021-01", "", "Cut settlement time by 40% with Spring Boot services on PostgreSQL.")
                .put("current", true);
        skills(document, "Java", "Spring Boot", "PostgreSQL", "REST APIs", "Docker");
        return document;
    }

    private ObjectNode document(String headline) {
        ObjectNode content = objectMapper.createObjectNode();
        content.put("schemaVersion", 1);
        ObjectNode basics = content.putObject("basics");
        basics.put("fullName", "Aarav Sharma");
        basics.put("headline", headline);
        basics.put("email", "aarav@example.com");
        basics.put("phone", "+977 9800000000");
        content.putArray("sections");
        return content;
    }

    private void skills(ObjectNode document, String... keywords) {
        ArrayNode list = section(document, "SKILLS").addObject().put("name", "Technical").putArray("keywords");
        for (String keyword : keywords) {
            list.add(keyword);
        }
    }

    private ObjectNode job(ArrayNode jobs, String start, String end, String description) {
        ObjectNode job = jobs.addObject();
        job.put("role", "Engineer");
        job.put("company", "Acme");
        job.put("startDate", start);
        job.put("endDate", end);
        job.put("description", description);
        return job;
    }

    private ArrayNode section(ObjectNode document, String type) {
        ObjectNode section = ((ArrayNode) document.get("sections")).addObject();
        section.put("id", UUID.randomUUID().toString());
        section.put("type", type);
        section.put("title", type);
        section.put("visible", true);
        return section.putArray("items");
    }

    private Cv persistCv(User user, ObjectNode content) {
        return cvRepository.save(Cv.builder()
                .user(user)
                .title("Analysis test")
                .templateKey(cvProperties.getDefaultTemplateKey())
                .locale(cvProperties.getDefaultLocale())
                .content(content)
                .build());
    }

    private RequestPostProcessor asUser(User user) {
        UserPrincipal principal = UserPrincipal.localUser(
                user.getId().toString(),
                user.getName(),
                user.getEmail(),
                user.getEmail(),
                user.getPasswordHash(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }
}

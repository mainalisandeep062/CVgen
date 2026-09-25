package io.github.mainalisandeep.cvgen.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mainalisandeep.cvgen.config.CvProperties;
import io.github.mainalisandeep.cvgen.entity.Cv;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.repository.CvRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.security.UserPrincipal;
import io.github.mainalisandeep.cvgen.support.PostgresContainerSupport;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/cvs/{id}/export.pdf: the file itself, the ownership check, and the part that matters
 * most for a renderer fed with user data - hostile content stays text.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class CvExportControllerTest extends PostgresContainerSupport {

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
                .email("export-owner-" + UUID.randomUUID() + "@test.com")
                .name("Owner")
                .emailVerified(true)
                .build());

        intruder = userRepository.save(User.builder()
                .email("export-intruder-" + UUID.randomUUID() + "@test.com")
                .name("Intruder")
                .emailVerified(true)
                .build());
    }

    @Test
    @DisplayName("A filled CV downloads as a PDF named after the candidate, with accented text intact")
    void exportsPdf() throws Exception {
        Cv cv = persistCv(owner, document("Zoë Ångström-Müller", "Senior Java Developer",
                "Built payment services at eSewa — 40% faster settlement."));

        byte[] pdf = mockMvc.perform(get("/api/cvs/{id}/export.pdf", cv.getId()).with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"Zoe_Angstrom_Muller_CV.pdf\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        String text = textOf(pdf);
        assertThat(text).contains("Zoë Ångström-Müller");
        assertThat(text).contains("Senior Java Developer");
        assertThat(text).contains("40% faster settlement");
        assertThat(text).contains("Jan 2023");
        assertThat(text).contains("Present");
        assertThat(text).contains("Java, Spring Boot");
        assertThat(text).doesNotContain("Should not appear");
    }

    @Test
    @DisplayName("Markup in a CV field is printed as text, never interpreted")
    void hostileContentStaysText() throws Exception {
        String hostile = "<script>alert(1)</script><img src=\"file:///etc/passwd\"/>";
        Cv cv = persistCv(owner, document(hostile, "Title", "Summary"));

        byte[] pdf = mockMvc.perform(get("/api/cvs/{id}/export.pdf", cv.getId()).with(asUser(owner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        String text = textOf(pdf);
        assertThat(text).contains("<script>alert(1)</script>");
        assertThat(text).doesNotContain("root:");
    }

    @Test
    @DisplayName("An empty starter CV still renders")
    void exportsEmptyCv() throws Exception {
        ObjectNode empty = objectMapper.createObjectNode();
        empty.put("schemaVersion", 1);
        empty.putArray("sections");
        Cv cv = persistCv(owner, empty);

        mockMvc.perform(get("/api/cvs/{id}/export.pdf", cv.getId()).with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Export_test_CV.pdf\""));
    }

    @Test
    @DisplayName("Exporting someone else's CV is 404, not 403")
    void foreignCvIsNotFound() throws Exception {
        Cv cv = persistCv(owner, document("Owner Name", "Title", "Summary"));

        mockMvc.perform(get("/api/cvs/{id}/export.pdf", cv.getId()).with(asUser(intruder)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(false));
    }

    @Test
    @DisplayName("Export requires authentication")
    void unauthenticatedIsRejected() throws Exception {
        Cv cv = persistCv(owner, document("Owner Name", "Title", "Summary"));

        mockMvc.perform(get("/api/cvs/{id}/export.pdf", cv.getId()))
                .andExpect(status().isUnauthorized());
    }

    private String textOf(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    /** A document shaped exactly like the frontend editor writes it. */
    private ObjectNode document(String fullName, String headline, String summary) {
        ObjectNode content = objectMapper.createObjectNode();
        content.put("schemaVersion", 1);

        ObjectNode basics = content.putObject("basics");
        basics.put("fullName", fullName);
        basics.put("headline", headline);
        basics.put("email", "candidate@example.com");
        basics.put("phone", "+977 9800000000");
        basics.put("location", "Kathmandu");
        basics.putArray("links").addObject().put("label", "LinkedIn").put("url", "linkedin.com/in/candidate");

        ArrayNode sections = content.putArray("sections");
        section(sections, "SUMMARY", "Summary").addObject().put("id", "s1").put("text", summary);

        ObjectNode job = section(sections, "EXPERIENCE", "Experience").addObject();
        job.put("id", "e1");
        job.put("role", "Backend Engineer");
        job.put("company", "Fonepay");
        job.put("startDate", "2023-01");
        job.put("endDate", "");
        job.put("current", true);
        job.put("description", "Owned the settlement pipeline.");

        ObjectNode skills = section(sections, "SKILLS", "Skills").addObject();
        skills.put("id", "k1");
        skills.put("name", "Technical");
        skills.putArray("keywords").add("Java").add("Spring Boot");

        ObjectNode hidden = sections.addObject();
        hidden.put("type", "PROJECTS");
        hidden.put("title", "Hidden projects");
        hidden.put("visible", false);
        hidden.putArray("items").addObject().put("name", "Should not appear");

        return content;
    }

    private ArrayNode section(ArrayNode sections, String type, String title) {
        ObjectNode section = sections.addObject();
        section.put("id", UUID.randomUUID().toString());
        section.put("type", type);
        section.put("title", title);
        section.put("visible", true);
        return section.putArray("items");
    }

    private Cv persistCv(User user, ObjectNode content) {
        return cvRepository.save(Cv.builder()
                .user(user)
                .title("Export test")
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

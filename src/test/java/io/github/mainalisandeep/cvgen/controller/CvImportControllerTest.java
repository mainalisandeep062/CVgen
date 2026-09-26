package io.github.mainalisandeep.cvgen.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import io.github.mainalisandeep.cvgen.config.CvProperties;
import io.github.mainalisandeep.cvgen.entity.Cv;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.repository.CvRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.security.UserPrincipal;
import io.github.mainalisandeep.cvgen.support.PostgresContainerSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/cvs/import and GET /api/cvs/import/github/{username}.
 * <p>
 * The PDF case is a round trip through this server's own export, so it tracks the real renderer
 * rather than a fixture that drifts. GitHub is a local HTTP server standing in for the API.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class CvImportControllerTest extends PostgresContainerSupport {

    private static HttpServer github;

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

    @BeforeAll
    static void startGithub() throws IOException {
        github = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        github.createContext("/users/aarav/repos", exchange -> respond(exchange, 200, """
                [
                  {"name":"khata","description":"Offline ledger","html_url":"https://github.com/aarav/khata",
                   "homepage":"","language":"Kotlin","stargazers_count":12,"fork":false,"private":false,
                   "pushed_at":"2026-05-01T10:00:00Z"},
                  {"name":"spring-boot","description":"fork","html_url":"https://github.com/aarav/spring-boot",
                   "homepage":null,"language":"Java","stargazers_count":0,"fork":true,"private":false,
                   "pushed_at":"2026-08-01T10:00:00Z"},
                  {"name":"nepali-date","description":null,"html_url":"https://github.com/aarav/nepali-date",
                   "homepage":"https://nepali-date.dev","language":"TypeScript","stargazers_count":3,"fork":false,
                   "private":false,"pushed_at":"2026-07-01T10:00:00Z"}
                ]"""));
        github.createContext("/users/ghost/repos", exchange -> respond(exchange, 404, "{\"message\":\"Not Found\"}"));
        github.createContext("/users/limited/repos", exchange -> respond(exchange, 403, "{\"message\":\"rate limit\"}"));
        github.start();
    }

    @AfterAll
    static void stopGithub() {
        github.stop(0);
    }

    @DynamicPropertySource
    static void githubUrl(DynamicPropertyRegistry registry) {
        registry.add("app.github.api-base-url", () -> "http://127.0.0.1:" + github.getAddress().getPort());
    }

    @BeforeEach
    void setUp() {
        cvRepository.deleteAll();
        owner = userRepository.save(User.builder()
                .email("import-owner-" + UUID.randomUUID() + "@test.com")
                .name("Owner")
                .emailVerified(true)
                .build());
    }

    @Test
    @DisplayName("A PDF exported by CVGen imports back into the same fields, and nothing is saved")
    void importsExportedPdf() throws Exception {
        Cv cv = persistCv(filledDocument());
        byte[] pdf = mockMvc.perform(get("/api/cvs/{id}/export.pdf", cv.getId()).with(asUser(owner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        long before = cvRepository.countByUserId(owner.getId());

        mockMvc.perform(multipart("/api/cvs/import")
                        .file(new MockMultipartFile("file", "cv.pdf", "application/pdf", pdf))
                        .with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.detected.fullName").value("Aarav Sharma"))
                .andExpect(jsonPath("$.data.detected.email").value("aarav@example.com"))
                .andExpect(jsonPath("$.data.detected.experience").value(1))
                .andExpect(jsonPath("$.data.detected.skills").value(3))
                .andExpect(jsonPath("$.data.content.schemaVersion").value(1))
                .andExpect(jsonPath("$.data.content.sections[?(@.type=='EXPERIENCE')].items[0].company")
                        .value(contains("Fonepay")))
                .andExpect(jsonPath("$.data.content.sections[?(@.type=='EXPERIENCE')].items[0].startDate")
                        .value(contains("2022-01")));

        assertThat(cvRepository.countByUserId(owner.getId())).isEqualTo(before);
    }

    @Test
    @DisplayName("A Word document is read paragraph by paragraph")
    void importsDocx() throws Exception {
        byte[] docx = docx("Sita Rai", "Frontend Developer", "sita@example.com",
                "Skills", "React, TypeScript, Figma");

        mockMvc.perform(multipart("/api/cvs/import")
                        .file(new MockMultipartFile("file", "cv.docx", "application/octet-stream", docx))
                        .with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.detected.fullName").value("Sita Rai"))
                .andExpect(jsonPath("$.data.detected.headline").value("Frontend Developer"))
                .andExpect(jsonPath("$.data.detected.skills").value(3))
                .andExpect(jsonPath("$.data.sections", contains("SKILLS")));
    }

    @Test
    @DisplayName("The type is judged by the bytes: a PNG named cv.pdf is refused")
    void refusesNonDocuments() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0};

        mockMvc.perform(multipart("/api/cvs/import")
                        .file(new MockMultipartFile("file", "cv.pdf", "application/pdf", png))
                        .with(asUser(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Upload a PDF or a Word (.docx) document"));
    }

    @Test
    @DisplayName("A file over the import cap is refused before it is parsed")
    void refusesOversizedFiles() throws Exception {
        byte[] big = new byte[(int) cvProperties.getImportMaxBytes() + 1];
        big[0] = '%';

        mockMvc.perform(multipart("/api/cvs/import")
                        .file(new MockMultipartFile("file", "cv.pdf", "application/pdf", big))
                        .with(asUser(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GitHub import lists own public repositories, forks dropped, newest push first")
    void listsGithubRepositories() throws Exception {
        mockMvc.perform(get("/api/cvs/import/github/{username}", "aarav").with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].name").value("nepali-date"))
                .andExpect(jsonPath("$.data[0].homepage").value("https://nepali-date.dev"))
                .andExpect(jsonPath("$.data[1].name").value("khata"))
                .andExpect(jsonPath("$.data[1].homepage").doesNotExist())
                .andExpect(jsonPath("$.data[1].stars").value(12));
    }

    @Test
    @DisplayName("GitHub errors map to 404, 429 and 400")
    void mapsGithubErrors() throws Exception {
        mockMvc.perform(get("/api/cvs/import/github/{username}", "ghost").with(asUser(owner)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/cvs/import/github/{username}", "limited").with(asUser(owner)))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(get("/api/cvs/import/github/{username}", "bad--name").with(asUser(owner)))
                .andExpect(status().isBadRequest());
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** The smallest .docx Word would open: one part, one paragraph per argument. */
    private static byte[] docx(String... paragraphs) throws IOException {
        StringBuilder body = new StringBuilder();
        for (String paragraph : paragraphs) {
            body.append("<w:p><w:r><w:t>").append(paragraph).append("</w:t></w:r></w:p>");
        }
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:body>" + body + "</w:body></w:document>";

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private ObjectNode filledDocument() {
        ObjectNode content = objectMapper.createObjectNode();
        content.put("schemaVersion", 1);
        ObjectNode basics = content.putObject("basics");
        basics.put("fullName", "Aarav Sharma");
        basics.put("headline", "Senior Java Developer");
        basics.put("email", "aarav@example.com");
        basics.put("phone", "+977 9801234567");
        basics.put("location", "Kathmandu, Nepal");
        basics.putArray("links");

        ArrayNode sections = content.putArray("sections");
        ObjectNode experience = sections.addObject().put("type", "EXPERIENCE").put("title", "Experience");
        experience.putArray("items").addObject()
                .put("role", "Backend Engineer")
                .put("company", "Fonepay")
                .put("startDate", "2022-01")
                .put("current", true)
                .put("description", "Built settlement services.");
        ObjectNode skills = sections.addObject().put("type", "SKILLS").put("title", "Skills");
        skills.putArray("items").addObject().put("name", "Technical")
                .putArray("keywords").add("Java").add("Spring Boot").add("PostgreSQL");
        return content;
    }

    private Cv persistCv(ObjectNode content) {
        return cvRepository.save(Cv.builder()
                .user(owner)
                .title("Import test")
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

package io.github.mainalisandeep.cvgen.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mainalisandeep.cvgen.config.CvProperties;
import io.github.mainalisandeep.cvgen.dto.CvCreateRequestDto;
import io.github.mainalisandeep.cvgen.dto.CvMetaUpdateRequestDto;
import io.github.mainalisandeep.cvgen.dto.CvUpdateRequestDto;
import io.github.mainalisandeep.cvgen.entity.Cv;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.enums.CvStatus;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CRUD over /api/cvs, and the cross-user checks that are the point of this endpoint.
 *
 * <p>The negative cases are written alongside the happy path rather than later on purpose: an
 * authorization check that looks right and quietly is not is the same class of bug as the
 * GitHub {@code email_verified} mismatch already fixed here. Every foreign CV must come back
 * 404 - a 403 confirms the id exists and turns a guess into an enumeration oracle.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class CvControllerTest extends PostgresContainerSupport {

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

    /**
     * Only the CV table is cleared, and both users get a unique email.
     * <p>
     * Wiping {@code users} here would fail the moment another test class has left an identity
     * row behind - {@code fk_user_identities_user_id} - and every assertion below is scoped to a
     * user id anyway, so rows belonging to other tests are invisible to it.
     */
    @BeforeEach
    void setUp() {
        cvRepository.deleteAll();

        owner = userRepository.save(User.builder()
                .email("cv-owner-" + UUID.randomUUID() + "@test.com")
                .name("Owner")
                .emailVerified(true)
                .build());

        intruder = userRepository.save(User.builder()
                .email("cv-intruder-" + UUID.randomUUID() + "@test.com")
                .name("Intruder")
                .emailVerified(true)
                .build());
    }

    // --- Create ---

    @Test
    @DisplayName("Create without content seeds the v1 starter document")
    void createSeedsStarterDocument() throws Exception {
        var request = CvCreateRequestDto.builder().title("Backend roles 2026").build();

        mockMvc.perform(post("/api/cvs")
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data.title").value("Backend roles 2026"))
                .andExpect(jsonPath("$.data.templateKey").value(cvProperties.getDefaultTemplateKey()))
                .andExpect(jsonPath("$.data.locale").value(cvProperties.getDefaultLocale()))
                .andExpect(jsonPath("$.data.status").value(CvStatus.DRAFT.name()))
                .andExpect(jsonPath("$.data.content.schemaVersion").value(1))
                .andExpect(jsonPath("$.data.content.sections[0].type").value("EXPERIENCE"))
                .andExpect(jsonPath("$.data.content.sections[1].type").value("EDUCATION"));

        assertThat(cvRepository.countByUserId(owner.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("A section type this server does not know survives a round trip")
    void createKeepsUnknownSectionTypes() throws Exception {
        ObjectNode content = document();
        ObjectNode section = ((ArrayNode) content.get("sections")).addObject();
        section.put("type", "PUBLICATIONS_WITH_DOIS");
        section.put("title", "Publications");

        var request = CvCreateRequestDto.builder().title("Academic").content(content).build();

        mockMvc.perform(post("/api/cvs")
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content.sections[0].type").value("PUBLICATIONS_WITH_DOIS"))
                .andExpect(jsonPath("$.data.content.sections[0].title").value("Publications"));
    }

    @Test
    @DisplayName("Content that is not a JSON object is rejected")
    void createRejectsNonObjectContent() throws Exception {
        String body = """
                {"title":"Broken","content":["not","an","object"]}""";

        mockMvc.perform(post("/api/cvs")
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(false));

        assertThat(cvRepository.countByUserId(owner.getId())).isZero();
    }

    @Test
    @DisplayName("A document declaring a newer schema version is rejected, not silently rewritten")
    void createRejectsUnknownSchemaVersion() throws Exception {
        ObjectNode content = document();
        content.put("schemaVersion", 99);

        var request = CvCreateRequestDto.builder().title("From the future").content(content).build();

        mockMvc.perform(post("/api/cvs")
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Content over the size cap is rejected")
    void createRejectsOversizedContent() throws Exception {
        ObjectNode content = document();
        content.put("padding", "x".repeat((int) cvProperties.getMaxContentBytes() + 1));

        var request = CvCreateRequestDto.builder().title("Huge").content(content).build();

        mockMvc.perform(post("/api/cvs")
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(cvRepository.countByUserId(owner.getId())).isZero();
    }

    @Test
    @DisplayName("A blank title fails validation before anything is stored")
    void createRejectsBlankTitle() throws Exception {
        var request = CvCreateRequestDto.builder().title("  ").build();

        mockMvc.perform(post("/api/cvs")
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(cvRepository.countByUserId(owner.getId())).isZero();
    }

    @Test
    @DisplayName("Creating past the per-user cap conflicts")
    void createStopsAtPerUserCap() throws Exception {
        for (int i = 0; i < cvProperties.getMaxPerUser(); i++) {
            persistCv(owner, "CV " + i);
        }

        var request = CvCreateRequestDto.builder().title("One too many").build();

        mockMvc.perform(post("/api/cvs")
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        assertThat(cvRepository.countByUserId(owner.getId())).isEqualTo(cvProperties.getMaxPerUser());
    }

    // --- Read ---

    @Test
    @DisplayName("The list contains the caller's own CVs and nobody else's")
    void listReturnsOwnCvsOnly() throws Exception {
        persistCv(owner, "Mine");
        persistCv(intruder, "Theirs");

        mockMvc.perform(get("/api/cvs").with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("Mine"));
    }

    @Test
    @DisplayName("Reading an own CV returns the stored document unchanged")
    void getOwnCv() throws Exception {
        Cv cv = persistCv(owner, "Mine");

        mockMvc.perform(get("/api/cvs/{cvId}", cv.getId()).with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(cv.getId().toString()))
                .andExpect(jsonPath("$.data.content.schemaVersion").value(1));
    }

    @Test
    @DisplayName("Reading another user's CV is 404, not 403")
    void getForeignCvIsNotFound() throws Exception {
        Cv cv = persistCv(owner, "Mine");

        mockMvc.perform(get("/api/cvs/{cvId}", cv.getId()).with(asUser(intruder)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(false));
    }

    @Test
    @DisplayName("A CV id that does not exist is 404")
    void getUnknownCvIsNotFound() throws Exception {
        mockMvc.perform(get("/api/cvs/{cvId}", UUID.randomUUID()).with(asUser(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("An unauthenticated caller never reaches the endpoint")
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/cvs"))
                .andExpect(status().isUnauthorized());
    }

    // --- Update ---

    @Test
    @DisplayName("Replacing content stores the new document")
    void replaceOwnContent() throws Exception {
        Cv cv = persistCv(owner, "Mine");

        ObjectNode content = document();
        content.putObject("basics").put("fullName", "Sandeep Mainali");

        mockMvc.perform(put("/api/cvs/{cvId}", cv.getId())
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CvUpdateRequestDto(content))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.basics.fullName").value("Sandeep Mainali"));

        Cv stored = cvRepository.findById(cv.getId()).orElseThrow();
        assertThat(stored.getContent().path("basics").path("fullName").asText())
                .isEqualTo("Sandeep Mainali");
    }

    @Test
    @DisplayName("Replacing another user's content is 404 and leaves the document untouched")
    void replaceForeignContentIsNotFound() throws Exception {
        Cv cv = persistCv(owner, "Mine");

        ObjectNode content = document();
        content.putObject("basics").put("fullName", "Not the owner");

        mockMvc.perform(put("/api/cvs/{cvId}", cv.getId())
                        .with(asUser(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CvUpdateRequestDto(content))))
                .andExpect(status().isNotFound());

        Cv stored = cvRepository.findById(cv.getId()).orElseThrow();
        assertThat(stored.getContent().path("basics").path("fullName").asText()).isEmpty();
    }

    @Test
    @DisplayName("Metadata patch touches only the fields it carries")
    void patchOwnMeta() throws Exception {
        Cv cv = persistCv(owner, "Mine");

        var request = CvMetaUpdateRequestDto.builder()
                .title("Renamed")
                .status(CvStatus.READY)
                .build();

        mockMvc.perform(patch("/api/cvs/{cvId}/meta", cv.getId())
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Renamed"))
                .andExpect(jsonPath("$.data.status").value(CvStatus.READY.name()))
                .andExpect(jsonPath("$.data.templateKey").value(cvProperties.getDefaultTemplateKey()))
                .andExpect(jsonPath("$.data.locale").value(cvProperties.getDefaultLocale()));
    }

    @Test
    @DisplayName("Patching another user's metadata is 404 and leaves the title alone")
    void patchForeignMetaIsNotFound() throws Exception {
        Cv cv = persistCv(owner, "Mine");

        var request = CvMetaUpdateRequestDto.builder().title("Renamed by intruder").build();

        mockMvc.perform(patch("/api/cvs/{cvId}/meta", cv.getId())
                        .with(asUser(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        assertThat(cvRepository.findById(cv.getId()).orElseThrow().getTitle()).isEqualTo("Mine");
    }

    // --- Delete ---

    @Test
    @DisplayName("Deleting an own CV removes the row")
    void deleteOwnCv() throws Exception {
        Cv cv = persistCv(owner, "Mine");

        mockMvc.perform(delete("/api/cvs/{cvId}", cv.getId()).with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true));

        assertThat(cvRepository.findById(cv.getId())).isEmpty();
    }

    @Test
    @DisplayName("Deleting another user's CV is 404 and the row survives")
    void deleteForeignCvIsNotFound() throws Exception {
        Cv cv = persistCv(owner, "Mine");

        mockMvc.perform(delete("/api/cvs/{cvId}", cv.getId()).with(asUser(intruder)))
                .andExpect(status().isNotFound());

        assertThat(cvRepository.findById(cv.getId())).isPresent();
    }

    // --- Helpers ---

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

    private Cv persistCv(User user, String title) {
        return cvRepository.save(Cv.builder()
                .user(user)
                .title(title)
                .templateKey(cvProperties.getDefaultTemplateKey())
                .locale(cvProperties.getDefaultLocale())
                .content(document())
                .build());
    }

    /** Minimal valid document: an object carrying the schema version this server writes. */
    private ObjectNode document() {
        ObjectNode content = objectMapper.createObjectNode();
        content.put("schemaVersion", 1);
        content.putArray("sections");
        return content;
    }
}

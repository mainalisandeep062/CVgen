package io.github.mainalisandeep.cvgen.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mainalisandeep.cvgen.config.CvProperties;
import io.github.mainalisandeep.cvgen.dto.AdminTemplateCreateRequestDto;
import io.github.mainalisandeep.cvgen.dto.AdminTemplateUpdateRequestDto;
import io.github.mainalisandeep.cvgen.dto.CvCreateRequestDto;
import io.github.mainalisandeep.cvgen.dto.CvMetaUpdateRequestDto;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.enums.CvSectionType;
import io.github.mainalisandeep.cvgen.enums.CvTemplateLayout;
import io.github.mainalisandeep.cvgen.repository.CvTemplateRepository;
import io.github.mainalisandeep.cvgen.support.AdminTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Template administration, and the rules that keep every stored row renderable: a template belongs to
 * a layout that exists in code, and shows only sections that layout can draw.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AdminTemplateControllerTest extends AdminTestSupport {

    @Autowired
    private CvTemplateRepository cvTemplateRepository;

    @Autowired
    private CvProperties cvProperties;

    private User admin;
    private User member;

    @BeforeEach
    void setUp() {
        admin = saveAdmin("template-admin");
        member = saveUser("template-member");
    }

    @Test
    @DisplayName("Layouts list the renderers a template can be based on")
    void listLayouts() throws Exception {
        mockMvc.perform(get("/api/admin/templates/layouts").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(CvTemplateLayout.values().length))
                .andExpect(jsonPath("$.data[0].key").value(CvTemplateLayout.CLASSIC.getKey()))
                .andExpect(jsonPath("$.data[0].supportedSections[0]").value(CvSectionType.SUMMARY.name()));
    }

    @Test
    @DisplayName("Creating a template defaults its sections to the layout's own")
    void createTemplate() throws Exception {
        String key = "modern-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/admin/templates")
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create(key).build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.key").value(key))
                .andExpect(jsonPath("$.data.layout").value(CvTemplateLayout.CLASSIC.getKey()))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.cvCount").value(0))
                .andExpect(jsonPath("$.data.supportedSections.length()")
                        .value(CvTemplateLayout.CLASSIC.getSupportedSections().size()));

        assertThat(cvTemplateRepository.existsByTemplateKey(key)).isTrue();
    }

    @Test
    @DisplayName("A free template cannot carry a credit cost")
    void createForcesZeroCostOnFreeTemplate() throws Exception {
        String key = "free-" + UUID.randomUUID().toString().substring(0, 8);
        var request = create(key).creditCost(500).build();

        mockMvc.perform(post("/api/admin/templates")
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.premium").value(false))
                .andExpect(jsonPath("$.data.creditCost").value(0));
    }

    @Test
    @DisplayName("A duplicate key conflicts")
    void duplicateKeyConflicts() throws Exception {
        var request = create(cvProperties.getDefaultTemplateKey()).build();

        mockMvc.perform(post("/api/admin/templates")
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(false));
    }

    @Test
    @DisplayName("A key that is not lowercase-and-hyphens fails validation")
    void invalidKeyIsRejected() throws Exception {
        var request = create("Not A Key").build();

        mockMvc.perform(post("/api/admin/templates")
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    @DisplayName("A section the layout cannot draw is rejected")
    void unsupportedSectionIsRejected() throws Exception {
        var request = create("sections-" + UUID.randomUUID().toString().substring(0, 8))
                .supportedSections(List.of(CvSectionType.SUMMARY, CvSectionType.CERTIFICATIONS))
                .build();

        mockMvc.perform(post("/api/admin/templates")
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(false));
    }

    @Test
    @DisplayName("A layout that does not exist in code is rejected")
    void unknownLayoutIsRejected() throws Exception {
        var request = create("layout-" + UUID.randomUUID().toString().substring(0, 8))
                .layout("holographic")
                .build();

        mockMvc.perform(post("/api/admin/templates")
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Update replaces the editable fields and leaves the key alone")
    void updateTemplate() throws Exception {
        String key = "update-" + UUID.randomUUID().toString().substring(0, 8);
        UUID id = createTemplate(key);

        var request = AdminTemplateUpdateRequestDto.builder()
                .name("Renamed")
                .layout(CvTemplateLayout.CLASSIC.getKey())
                .accentColor("#112233")
                .premium(true)
                .creditCost(12)
                .sortOrder(3)
                .build();

        mockMvc.perform(put("/api/admin/templates/{templateId}", id)
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value(key))
                .andExpect(jsonPath("$.data.name").value("Renamed"))
                .andExpect(jsonPath("$.data.accentColor").value("#112233"))
                .andExpect(jsonPath("$.data.premium").value(true))
                .andExpect(jsonPath("$.data.creditCost").value(12));
    }

    @Test
    @DisplayName("Deleting an unused template removes it")
    void deleteTemplate() throws Exception {
        UUID id = createTemplate("delete-" + UUID.randomUUID().toString().substring(0, 8));

        mockMvc.perform(delete("/api/admin/templates/{templateId}", id).with(as(admin)))
                .andExpect(status().isOk());

        assertThat(cvTemplateRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("Deleting a template CVs still use conflicts")
    void deleteInUseTemplateConflicts() throws Exception {
        String key = "inuse-" + UUID.randomUUID().toString().substring(0, 8);
        UUID id = createTemplate(key);
        createCv("Using it", key);

        mockMvc.perform(delete("/api/admin/templates/{templateId}", id).with(as(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(false));

        assertThat(cvTemplateRepository.findById(id)).isPresent();
    }

    @Test
    @DisplayName("The default template can be neither deleted nor deactivated")
    void defaultTemplateIsProtected() throws Exception {
        UUID defaultId = cvTemplateRepository.findByTemplateKeyAndActiveTrue(cvProperties.getDefaultTemplateKey())
                .orElseThrow()
                .getId();

        mockMvc.perform(delete("/api/admin/templates/{templateId}", defaultId).with(as(admin)))
                .andExpect(status().isConflict());

        var deactivate = AdminTemplateUpdateRequestDto.builder()
                .name("Classic")
                .layout(CvTemplateLayout.CLASSIC.getKey())
                .active(false)
                .build();

        mockMvc.perform(put("/api/admin/templates/{templateId}", defaultId)
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deactivate)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("An inactive template is hidden from the picker and refused on create, but a CV already using it can still be saved")
    void inactiveTemplateRules() throws Exception {
        String key = "seasonal-" + UUID.randomUUID().toString().substring(0, 8);
        UUID id = createTemplate(key);

        UUID cvId = createCv("Seasonal CV", key);
        deactivate(id, key);

        // Hidden from the picker
        mockMvc.perform(get("/api/templates").with(as(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.key=='" + key + "')]").isEmpty());

        // Refused for a new CV
        mockMvc.perform(post("/api/cvs")
                        .with(as(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                CvCreateRequestDto.builder().title("New").templateKey(key).build())))
                .andExpect(status().isBadRequest());

        // But re-sending the CV's own key is not a switch, so a rename still works
        mockMvc.perform(patch("/api/cvs/{cvId}/meta", cvId)
                        .with(as(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                CvMetaUpdateRequestDto.builder().title("Renamed").templateKey(key).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Renamed"))
                .andExpect(jsonPath("$.data.templateKey").value(key));

        // Switching another CV to it is still refused
        UUID otherCv = createCv("Other", cvProperties.getDefaultTemplateKey());
        mockMvc.perform(patch("/api/cvs/{cvId}/meta", otherCv)
                        .with(as(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                CvMetaUpdateRequestDto.builder().templateKey(key).build())))
                .andExpect(status().isBadRequest());
    }

    // --- Helpers ---

    private AdminTemplateCreateRequestDto.AdminTemplateCreateRequestDtoBuilder<?, ?> create(String key) {
        return AdminTemplateCreateRequestDto.builder()
                .key(key)
                .name("Template " + key)
                .layout(CvTemplateLayout.CLASSIC.getKey());
    }

    private UUID createTemplate(String key) throws Exception {
        String body = mockMvc.perform(post("/api/admin/templates")
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create(key).build())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(read(body).path("data").path("id").asText());
    }

    private void deactivate(UUID templateId, String key) throws Exception {
        var request = AdminTemplateUpdateRequestDto.builder()
                .name("Template " + key)
                .layout(CvTemplateLayout.CLASSIC.getKey())
                .active(false)
                .build();

        mockMvc.perform(put("/api/admin/templates/{templateId}", templateId)
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }

    private UUID createCv(String title, String templateKey) throws Exception {
        String body = mockMvc.perform(post("/api/cvs")
                        .with(as(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                CvCreateRequestDto.builder().title(title).templateKey(templateKey).build())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(read(body).path("data").path("id").asText());
    }

    private JsonNode read(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}

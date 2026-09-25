package io.github.mainalisandeep.cvgen.controller;

import io.github.mainalisandeep.cvgen.dto.NotificationCreateRequestDto;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.enums.NotificationAudience;
import io.github.mainalisandeep.cvgen.enums.NotificationLevel;
import io.github.mainalisandeep.cvgen.support.AdminTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sending notifications and reading them.
 *
 * <p>The rule under test on the broadcast side is that visibility is computed, not fanned out: a
 * broadcast reaches accounts that existed when it was sent and nobody else. "Created later" is forced
 * with SQL rather than by hoping two timestamps differ.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AdminNotificationControllerTest extends AdminTestSupport {

    @Autowired
    private EntityManager entityManager;

    private User admin;
    private User existingUser;
    private User laterUser;

    @BeforeEach
    void setUp() {
        admin = saveAdmin("notify-admin");
        existingUser = saveUser("notify-existing");
        laterUser = saveUser("notify-later");
    }

    @Test
    @DisplayName("A broadcast reaches accounts that existed when it was sent, and not those created after")
    void broadcastVisibility() throws Exception {
        UUID notificationId = send(broadcast("Scheduled maintenance").build());
        createdAfterTheBroadcast(laterUser);

        mockMvc.perform(get("/api/notifications").with(as(existingUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(notificationId.toString()))
                .andExpect(jsonPath("$.data.items[0].title").value("Scheduled maintenance"))
                .andExpect(jsonPath("$.data.items[0].read").value(false))
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        mockMvc.perform(get("/api/notifications").with(as(laterUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    @DisplayName("A direct notification reaches only its recipient")
    void directNotificationReachesRecipientOnly() throws Exception {
        UUID notificationId = send(direct(existingUser, "Your export is ready").build());

        mockMvc.perform(get("/api/notifications").with(as(existingUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(notificationId.toString()));

        mockMvc.perform(get("/api/notifications").with(as(laterUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("Marking someone else's notification read is 404, not 403")
    void markingForeignNotificationIsNotFound() throws Exception {
        UUID notificationId = send(direct(existingUser, "Private").build());

        mockMvc.perform(post("/api/notifications/{id}/read", notificationId).with(as(laterUser)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(false));
    }

    @Test
    @DisplayName("Marking as read is idempotent and shows up as a read count for admins")
    void markReadIsIdempotentAndCounted() throws Exception {
        UUID notificationId = send(direct(existingUser, "Read me").build());

        mockMvc.perform(post("/api/notifications/{id}/read", notificationId).with(as(existingUser)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/notifications/{id}/read", notificationId).with(as(existingUser)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications").with(as(existingUser)))
                .andExpect(jsonPath("$.data.items[0].read").value(true))
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        mockMvc.perform(get("/api/admin/notifications").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(notificationId.toString()))
                .andExpect(jsonPath("$.data.items[0].recipientEmail").value(existingUser.getEmail()))
                .andExpect(jsonPath("$.data.items[0].readCount").value(1))
                .andExpect(jsonPath("$.data.items[0].createdByEmail").value(admin.getEmail()));
    }

    @Test
    @DisplayName("Read-all clears the unread count")
    void readAllClearsUnreadCount() throws Exception {
        send(broadcast("First").build());
        send(broadcast("Second").build());
        send(direct(existingUser, "Third").build());

        mockMvc.perform(get("/api/notifications").with(as(existingUser)))
                .andExpect(jsonPath("$.data.unreadCount").value(3));

        mockMvc.perform(post("/api/notifications/read-all").with(as(existingUser)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications").with(as(existingUser)))
                .andExpect(jsonPath("$.data.unreadCount").value(0))
                .andExpect(jsonPath("$.data.items[0].read").value(true));
    }

    @Test
    @DisplayName("Deleting a notification takes it out of every inbox")
    void deleteNotification() throws Exception {
        UUID notificationId = send(direct(existingUser, "Retracted").build());
        mockMvc.perform(post("/api/notifications/{id}/read", notificationId).with(as(existingUser)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/notifications/{id}", notificationId).with(as(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications").with(as(existingUser)))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("A direct notification to an unknown address is 404")
    void unknownRecipientIsNotFound() throws Exception {
        var request = broadcast("Nobody")
                .audience(NotificationAudience.USER)
                .recipientEmail("nobody-" + UUID.randomUUID() + "@test.com")
                .build();

        mockMvc.perform(post("/api/admin/notifications")
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A direct notification without a recipient, and an off-site link, fail validation")
    void invalidNotificationsAreRejected() throws Exception {
        var noRecipient = broadcast("Missing").audience(NotificationAudience.USER).build();
        mockMvc.perform(post("/api/admin/notifications")
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noRecipient)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());

        // Protocol-relative, which a browser would follow off-site.
        var badLink = broadcast("Bad link").link("//evil.example.com").build();
        mockMvc.perform(post("/api/admin/notifications")
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLink)))
                .andExpect(status().isBadRequest());
    }

    // --- Helpers ---

    private NotificationCreateRequestDto.NotificationCreateRequestDtoBuilder broadcast(String title) {
        return NotificationCreateRequestDto.builder()
                .title(title)
                .body("Body of " + title)
                .level(NotificationLevel.INFO)
                .audience(NotificationAudience.ALL);
    }

    private NotificationCreateRequestDto.NotificationCreateRequestDtoBuilder direct(User recipient, String title) {
        return broadcast(title)
                .audience(NotificationAudience.USER)
                .recipientEmail(recipient.getEmail());
    }

    private UUID send(NotificationCreateRequestDto request) throws Exception {
        String body = mockMvc.perform(post("/api/admin/notifications")
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
    }

    /** created_at is written by Hibernate and never updated, so ageing an account needs raw SQL. */
    private void createdAfterTheBroadcast(User user) {
        entityManager.createNativeQuery("UPDATE users SET created_at = now() + interval '1 hour' WHERE id = :id")
                .setParameter("id", user.getId())
                .executeUpdate();
        entityManager.clear();
    }
}

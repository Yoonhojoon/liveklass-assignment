package com.liveklass.demo.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationType;
import com.liveklass.demo.notification.dto.NotificationCreateRequest;
import com.liveklass.demo.notification.repository.NotificationDeliveryJobRepository;
import com.liveklass.demo.notification.repository.NotificationInboxRepository;
import com.liveklass.demo.notification.repository.NotificationRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "notification.worker.enabled=false")
@AutoConfigureMockMvc
class NotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationRequestRepository repository;

    @Autowired
    private NotificationDeliveryJobRepository deliveryJobRepository;

    @Autowired
    private NotificationInboxRepository inboxRepository;

    @BeforeEach
    void clean() {
        inboxRepository.deleteAll();
        deliveryJobRepository.deleteAll();
        repository.deleteAll();
    }

    @Test
    void postNotificationReturnsImmediateAcceptanceAndDuplicateReturnsExistingId() throws Exception {
        String body = objectMapper.writeValueAsString(request("payment-1001"));

        MvcResult first = mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.duplicated").value(false))
                .andReturn();

        MvcResult second = mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.duplicated").value(true))
                .andReturn();

        long firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asLong();
        long secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asLong();
        assertThat(secondId).isEqualTo(firstId);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(deliveryJobRepository.count()).isEqualTo(1);
        assertThat(inboxRepository.count()).isEqualTo(1);
    }

    @Test
    void statusLookupExposesRetryFailureAndTimestamps() throws Exception {
        long id = create("payment-1002");

        mockMvc.perform(get("/api/notifications/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.recipientId").value("user-1"))
                .andExpect(jsonPath("$.notificationType").value("PAYMENT_CONFIRMED"))
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.retryCount").value(0))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void userListSupportsReadAndUnreadFilters() throws Exception {
        long unreadId = create("payment-unread");
        long readId = create("payment-read");

        mockMvc.perform(patch("/api/notifications/{id}/read", readId)
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true))
                .andExpect(jsonPath("$.readAt").exists());

        mockMvc.perform(get("/api/users/{recipientId}/notifications", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/users/{recipientId}/notifications", "user-1").param("read", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(readId));

        mockMvc.perform(get("/api/users/{recipientId}/notifications", "user-1").param("read", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(unreadId));
    }

    @Test
    void readEndpointIsIdempotentAndPreservesFirstReadAt() throws Exception {
        long id = create("payment-read-idempotent");

        MvcResult first = mockMvc.perform(patch("/api/notifications/{id}/read", id)
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult second = mockMvc.perform(patch("/api/notifications/{id}/read", id)
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstJson = objectMapper.readTree(first.getResponse().getContentAsString());
        JsonNode secondJson = objectMapper.readTree(second.getResponse().getContentAsString());
        assertThat(secondJson.get("readAt").asText()).isEqualTo(firstJson.get("readAt").asText());
    }

    private long create(String eventId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(eventId))))
                .andExpect(status().isAccepted())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private NotificationCreateRequest request(String eventId) {
        return new NotificationCreateRequest(
                "user-1",
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                eventId,
                "결제가 완료되었습니다",
                "결제 확정 알림입니다."
        );
    }
}

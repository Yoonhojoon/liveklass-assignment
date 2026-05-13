package com.liveklass.demo.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationStatus;
import com.liveklass.demo.notification.dto.NotificationRetryRequest;
import com.liveklass.demo.notification.domain.NotificationType;
import com.liveklass.demo.notification.dto.NotificationCreateRequest;
import com.liveklass.demo.notification.dto.NotificationTemplateEnabledRequest;
import com.liveklass.demo.notification.dto.NotificationTemplateUpsertRequest;
import com.liveklass.demo.notification.repository.NotificationDeliveryJobRepository;
import com.liveklass.demo.notification.repository.NotificationInboxRepository;
import com.liveklass.demo.notification.repository.NotificationRetryAuditRepository;
import com.liveklass.demo.notification.repository.NotificationRequestRepository;
import com.liveklass.demo.notification.repository.NotificationTemplateRepository;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@DisplayName("알림 API 통합 테스트")
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

    @Autowired
    private NotificationTemplateRepository templateRepository;

    @Autowired
    private NotificationRetryAuditRepository retryAuditRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clean() {
        inboxRepository.deleteAll();
        retryAuditRepository.deleteAll();
        deliveryJobRepository.deleteAll();
        templateRepository.deleteAll();
        repository.deleteAll();
    }

    @Nested
    @DisplayName("요청 등록")
    class CreateNotification {

        @Test
        @DisplayName("요청을 즉시 접수하고 중복 요청은 기존 ID를 반환한다")
        void postNotificationReturnsImmediateAcceptanceAndDuplicateReturnsExistingId() throws Exception {
            String body = objectMapper.writeValueAsString(request("payment-1001"));

            MvcResult first = mockMvc.perform(post("/api/notifications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.code").value("COMMON2020"))
                    .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                    .andExpect(jsonPath("$.data.duplicated").value(false))
                    .andReturn();

            MvcResult second = mockMvc.perform(post("/api/notifications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.code").value("COMMON2020"))
                    .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                    .andExpect(jsonPath("$.data.duplicated").value(true))
                    .andReturn();

            long firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("data").get("id").asLong();
            long secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("data").get("id").asLong();
            assertThat(secondId).isEqualTo(firstId);
            assertThat(repository.count()).isEqualTo(1);
            assertThat(deliveryJobRepository.count()).isEqualTo(1);
            assertThat(inboxRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("잘못된 요청은 ProblemDetail 형식의 에러 응답을 반환한다")
        void invalidRequestReturnsProblemDetailError() throws Exception {
            String body = objectMapper.writeValueAsString(new NotificationCreateRequest(
                    "",
                    NotificationType.PAYMENT_CONFIRMED,
                    NotificationChannel.EMAIL,
                    "payment-invalid",
                    "결제가 완료되었습니다",
                    "결제 확정 알림입니다.",
                    null,
                    null
            ));

            mockMvc.perform(post("/api/notifications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("알림 요청이 올바르지 않습니다."))
                    .andExpect(jsonPath("$.detail").value("recipientId is required"))
                    .andExpect(jsonPath("$.code").value("NOTIFICATION4000"));
        }

        @Test
        @DisplayName("필수 enum 값이 없으면 ProblemDetail 형식의 에러 응답을 반환한다")
        void nullEnumRequestReturnsProblemDetailError() throws Exception {
            String body = objectMapper.writeValueAsString(new NotificationCreateRequest(
                    "user-1",
                    null,
                    NotificationChannel.EMAIL,
                    "payment-null-type",
                    "결제가 완료되었습니다",
                    "결제 확정 알림입니다.",
                    null,
                    null
            ));

            mockMvc.perform(post("/api/notifications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("알림 요청이 올바르지 않습니다."))
                    .andExpect(jsonPath("$.detail").value("notificationType is required"))
                    .andExpect(jsonPath("$.code").value("NOTIFICATION4000"));
        }
    }

    @Nested
    @DisplayName("상태 조회")
    class StatusLookup {

        @Test
        @DisplayName("현재 상태와 재시도/시간 정보를 반환한다")
        void statusLookupExposesRetryFailureAndTimestamps() throws Exception {
            long id = create("payment-1002");
            Instant now = Instant.now();
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    deliveryJobRepository.claimForProcessing(
                            id,
                            "worker-api",
                            now.plusSeconds(600),
                            now,
                            NotificationStatus.REQUESTED,
                            NotificationStatus.RETRY_WAITING,
                            NotificationStatus.PROCESSING
                    ));

            mockMvc.perform(get("/api/notifications/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.code").value("COMMON2000"))
                    .andExpect(jsonPath("$.data.id").value(id))
                    .andExpect(jsonPath("$.data.recipientId").value("user-1"))
                    .andExpect(jsonPath("$.data.notificationType").value("PAYMENT_CONFIRMED"))
                    .andExpect(jsonPath("$.data.channel").value("EMAIL"))
                    .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                    .andExpect(jsonPath("$.data.retryCount").value(0))
                    .andExpect(jsonPath("$.data.scheduledAt").doesNotExist())
                    .andExpect(jsonPath("$.data.lockedBy").value("worker-api"))
                    .andExpect(jsonPath("$.data.lockedUntil").exists())
                    .andExpect(jsonPath("$.data.retryable").value(false))
                    .andExpect(jsonPath("$.data.terminal").value(false))
                    .andExpect(jsonPath("$.data.createdAt").exists())
                    .andExpect(jsonPath("$.data.updatedAt").exists());
        }

        @Test
        @DisplayName("없는 알림은 ProblemDetail 형식의 에러 응답을 반환한다")
        void notFoundReturnsProblemDetailError() throws Exception {
            mockMvc.perform(get("/api/notifications/{id}", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("알림을 찾을 수 없습니다."))
                    .andExpect(jsonPath("$.detail").value("Notification not found: 999"))
                    .andExpect(jsonPath("$.code").value("NOTIFICATION4004"));
        }
    }

    @Nested
    @DisplayName("예약 발송/템플릿/수동 재시도")
    class OptionalFeatures {

        @Test
        @DisplayName("예약 알림은 scheduledAt을 노출하고 inbox에 즉시 보인다")
        void scheduledNotificationExposesScheduledAtAndIsVisibleInInbox() throws Exception {
            Instant scheduledAt = Instant.parse("2026-05-14T09:00:00Z");
            String body = objectMapper.writeValueAsString(new NotificationCreateRequest(
                    "user-1",
                    NotificationType.PAYMENT_CONFIRMED,
                    NotificationChannel.EMAIL,
                    "payment-scheduled",
                    "예약 제목",
                    "예약 본문",
                    null,
                    scheduledAt
            ));

            MvcResult result = mockMvc.perform(post("/api/notifications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.data.scheduledAt").value(scheduledAt.toString()))
                    .andReturn();

            long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();

            mockMvc.perform(get("/api/notifications/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.scheduledAt").value(scheduledAt.toString()));

            mockMvc.perform(get("/api/users/{recipientId}/notifications", "user-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value(id))
                    .andExpect(jsonPath("$.data[0].scheduledAt").value(scheduledAt.toString()));
        }

        @Test
        @DisplayName("템플릿 CRUD와 템플릿 기반 생성이 동작한다")
        void templateCrudAndTemplateBasedCreateWork() throws Exception {
            mockMvc.perform(post("/api/notification-templates/{notificationType}/{channel}",
                            NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new NotificationTemplateUpsertRequest(
                                    "안녕하세요 ${userName}",
                                    "결제 금액은 ${amount}원입니다.",
                                    true
                            ))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.notificationType").value("PAYMENT_CONFIRMED"))
                    .andExpect(jsonPath("$.data.channel").value("EMAIL"))
                    .andExpect(jsonPath("$.data.enabled").value(true));

            mockMvc.perform(patch("/api/notification-templates/{notificationType}/{channel}/enabled",
                            NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new NotificationTemplateEnabledRequest(true))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.enabled").value(true));

            String createBody = objectMapper.writeValueAsString(new NotificationCreateRequest(
                    "user-1",
                    NotificationType.PAYMENT_CONFIRMED,
                    NotificationChannel.EMAIL,
                    "payment-template",
                    null,
                    null,
                    Map.of("userName", "kim", "amount", "10000"),
                    null
            ));

            MvcResult created = mockMvc.perform(post("/api/notifications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBody))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                    .andReturn();

            long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("data").get("id").asLong();
            mockMvc.perform(get("/api/notifications/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("안녕하세요 kim"))
                    .andExpect(jsonPath("$.data.message").value("결제 금액은 10000원입니다."));
        }

        @Test
        @DisplayName("템플릿 enabled 요청에서 필드가 누락되면 400을 반환한다")
        void missingEnabledFieldReturnsBadRequest() throws Exception {
            mockMvc.perform(post("/api/notification-templates/{notificationType}/{channel}",
                            NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new NotificationTemplateUpsertRequest(
                                    "안녕하세요 ${userName}",
                                    "결제 금액은 ${amount}원입니다.",
                                    true
                            ))))
                    .andExpect(status().isOk());

            mockMvc.perform(patch("/api/notification-templates/{notificationType}/{channel}/enabled",
                            NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("enabled is required"));
        }

        @Test
        @DisplayName("비활성 템플릿으로 생성하면 400을 반환한다")
        void disabledTemplateCreateReturnsBadRequest() throws Exception {
            mockMvc.perform(post("/api/notification-templates/{notificationType}/{channel}",
                            NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new NotificationTemplateUpsertRequest(
                                    "안녕하세요 ${userName}",
                                    "결제 금액은 ${amount}원입니다.",
                                    false
                            ))))
                    .andExpect(status().isOk());

            String createBody = objectMapper.writeValueAsString(new NotificationCreateRequest(
                    "user-1",
                    NotificationType.PAYMENT_CONFIRMED,
                    NotificationChannel.EMAIL,
                    "payment-template-disabled",
                    null,
                    null,
                    Map.of("userName", "kim", "amount", "10000"),
                    null
            ));

            mockMvc.perform(post("/api/notifications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("template is disabled for PAYMENT_CONFIRMED/EMAIL"));
        }

        @Test
        @DisplayName("잘못된 placeholder 문법 템플릿은 400을 반환한다")
        void malformedPlaceholderTemplateReturnsBadRequest() throws Exception {
            mockMvc.perform(post("/api/notification-templates/{notificationType}/{channel}",
                            NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new NotificationTemplateUpsertRequest(
                                    "안녕하세요 ${userName} ${broken",
                                    "결제 금액은 ${amount}원입니다.",
                                    true
                            ))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("template contains unsupported placeholder syntax"));
        }

        @Test
        @DisplayName("너무 긴 템플릿은 중복 에러가 아니라 길이 검증 에러를 반환한다")
        void oversizedTemplateReturnsLengthValidationError() throws Exception {
            String longTitle = "x".repeat(201);

            mockMvc.perform(post("/api/notification-templates/{notificationType}/{channel}",
                            NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new NotificationTemplateUpsertRequest(
                                    longTitle,
                                    "본문",
                                    true
                            ))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("titleTemplate exceeds 200 characters"));
        }

        @Test
        @DisplayName("최종 실패 알림은 수동 재시도로 같은 ID를 유지한 채 REQUESTED로 돌아간다")
        void manualRetryResetsFailedNotificationInPlace() throws Exception {
            long id = create("payment-retry");
            Instant now = Instant.now();
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                deliveryJobRepository.claimForProcessing(
                        id,
                        "worker-api",
                        now.plusSeconds(600),
                        now,
                        NotificationStatus.REQUESTED,
                        NotificationStatus.RETRY_WAITING,
                        NotificationStatus.PROCESSING
                );
                deliveryJobRepository.findById(id).orElseThrow().markFailed(3, "smtp down", now);
            });

            mockMvc.perform(post("/api/notifications/{id}/retry", id)
                            .header("X-Operator-Id", "admin-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new NotificationRetryRequest("fixed smtp"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(id))
                    .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                    .andExpect(jsonPath("$.data.retryCount").value(0))
                    .andExpect(jsonPath("$.data.lastFailureReason").doesNotExist())
                    .andExpect(jsonPath("$.data.failedAt").doesNotExist());

            assertThat(retryAuditRepository.findByRequestIdOrderByRetriedAtAsc(id)).hasSize(1);
            mockMvc.perform(get("/api/users/{recipientId}/notifications", "user-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value(id));
        }

        @Test
        @DisplayName("FAILED가 아닌 알림은 수동 재시도할 수 없다")
        void manualRetryRejectsNonFailedNotification() throws Exception {
            long id = create("payment-retry-reject");

            mockMvc.perform(post("/api/notifications/{id}/retry", id)
                            .header("X-Operator-Id", "admin-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new NotificationRetryRequest("retry now"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("manual retry is allowed only for FAILED notifications"));
        }
    }

    @Nested
    @DisplayName("사용자 알림함")
    class UserInbox {

        @Test
        @DisplayName("수신자 목록에서 읽음/안읽음 필터를 지원한다")
        void userListSupportsReadAndUnreadFilters() throws Exception {
            long unreadId = create("payment-unread");
            long readId = create("payment-read");

            mockMvc.perform(patch("/api/notifications/{id}/read", readId)
                            .header("X-User-Id", "user-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.read").value(true))
                    .andExpect(jsonPath("$.data.readAt").exists());

            mockMvc.perform(get("/api/users/{recipientId}/notifications", "user-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].retryCount").exists())
                    .andExpect(jsonPath("$.data[0].retryable").value(true))
                    .andExpect(jsonPath("$.data[0].terminal").value(false));

            mockMvc.perform(get("/api/users/{recipientId}/notifications", "user-1").param("read", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(readId));

            mockMvc.perform(get("/api/users/{recipientId}/notifications", "user-1").param("read", "false"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(unreadId));
        }

        @Test
        @DisplayName("읽음 처리는 멱등이고 최초 읽음 시각을 보존한다")
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
            assertThat(secondJson.get("data").get("readAt").asText()).isEqualTo(firstJson.get("data").get("readAt").asText());
        }
    }

    private long create(String eventId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(eventId))))
                .andExpect(status().isAccepted())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    private NotificationCreateRequest request(String eventId) {
        return new NotificationCreateRequest(
                "user-1",
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                eventId,
                "결제가 완료되었습니다",
                "결제 확정 알림입니다.",
                null,
                null
        );
    }
}

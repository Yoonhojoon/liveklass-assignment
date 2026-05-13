package com.liveklass.demo.notification.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationType;
import com.liveklass.demo.notification.dto.NotificationCreateRequest;
import com.liveklass.demo.notification.repository.NotificationDeliveryJobRepository;
import com.liveklass.demo.notification.repository.NotificationInboxRepository;
import com.liveklass.demo.notification.repository.NotificationRetryAuditRepository;
import com.liveklass.demo.notification.repository.NotificationRequestRepository;
import com.liveklass.demo.notification.repository.NotificationTemplateRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@DisplayName("알림 E2E 테스트")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "notification.worker.enabled=true",
                "notification.worker.poll-delay-ms=100",
                "notification.worker.batch-size=5"
        }
)
class NotificationE2eTest {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationRequestRepository requestRepository;

    @Autowired
    private NotificationDeliveryJobRepository deliveryJobRepository;

    @Autowired
    private NotificationInboxRepository inboxRepository;

    @Autowired
    private NotificationTemplateRepository templateRepository;

    @Autowired
    private NotificationRetryAuditRepository retryAuditRepository;

    @BeforeEach
    void clean() {
        inboxRepository.deleteAll();
        retryAuditRepository.deleteAll();
        deliveryJobRepository.deleteAll();
        templateRepository.deleteAll();
        requestRepository.deleteAll();
    }

    @Test
    @DisplayName("실제 HTTP 요청으로 접수한 알림을 스케줄러 워커가 발송 완료한다")
    void realHttpRequestIsAcceptedAndSentByScheduledWorker() throws Exception {
        NotificationCreateRequest createRequest = new NotificationCreateRequest(
                "user-e2e",
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                "payment-e2e",
                "결제가 완료되었습니다",
                "결제 확정 알림입니다.",
                null,
                null
        );

        HttpResponse<String> createResponse = httpClient.send(
                HttpRequest.newBuilder(api("/api/notifications"))
                        .timeout(Duration.ofSeconds(3))
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(createRequest)))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(createResponse.statusCode()).isEqualTo(202);
        JsonNode createJson = objectMapper.readTree(createResponse.body());
        long id = createJson.get("data").get("id").asLong();
        assertThat(createJson.get("data").get("status").asString()).isEqualTo("REQUESTED");

        JsonNode sent = waitUntilSent(id);

        assertThat(sent.get("success").asBoolean()).isTrue();
        assertThat(sent.get("data").get("status").asString()).isEqualTo("SENT");
        assertThat(sent.get("data").get("retryCount").asInt()).isZero();
        assertThat(sent.get("data").get("lastFailureReason").isNull()).isTrue();
        assertThat(sent.get("data").get("sentAt").isNull()).isFalse();
    }

    private JsonNode waitUntilSent(long id) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        JsonNode latest = null;
        while (System.nanoTime() < deadline) {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(api("/api/notifications/" + id))
                            .timeout(Duration.ofSeconds(3))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertThat(response.statusCode()).isEqualTo(200);
            latest = objectMapper.readTree(response.body());
            if ("SENT".equals(latest.get("data").get("status").asString())) {
                return latest;
            }
            Thread.sleep(100);
        }
        assertThat(latest)
                .as("notification should become SENT within timeout")
                .isNotNull();
        return latest;
    }

    private URI api(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}

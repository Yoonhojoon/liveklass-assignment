package com.liveklass.demo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationDeliveryJob;
import com.liveklass.demo.notification.domain.NotificationInbox;
import com.liveklass.demo.notification.domain.NotificationTemplate;
import com.liveklass.demo.notification.domain.NotificationStatus;
import com.liveklass.demo.notification.domain.NotificationType;
import com.liveklass.demo.notification.exception.NotificationValidationException;
import com.liveklass.demo.notification.repository.NotificationDeliveryJobRepository;
import com.liveklass.demo.notification.repository.NotificationInboxRepository;
import com.liveklass.demo.notification.repository.NotificationRetryAuditRepository;
import com.liveklass.demo.notification.repository.NotificationRequestRepository;
import com.liveklass.demo.notification.repository.NotificationTemplateRepository;
import com.liveklass.demo.notification.service.dto.NotificationCreateCommand;
import com.liveklass.demo.notification.service.dto.NotificationCreateResult;
import com.liveklass.demo.notification.service.dto.NotificationDetails;
import jakarta.persistence.EntityManagerFactory;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@DisplayName("알림 요청 서비스 테스트")
@SpringBootTest(properties = {
        "notification.worker.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Import(NotificationRequestServiceTest.FixedClockConfiguration.class)
class NotificationRequestServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-13T00:00:00Z");

    @Autowired
    private NotificationRequestService service;

    @Autowired
    private NotificationTemplateService templateService;

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

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void clean() {
        inboxRepository.deleteAll();
        retryAuditRepository.deleteAll();
        deliveryJobRepository.deleteAll();
        templateRepository.deleteAll();
        requestRepository.deleteAll();
    }

    @Nested
    @DisplayName("요청 생성")
    class CreateRequest {

        @Test
        @DisplayName("요청, 발송 작업, 알림함을 생성하고 발송은 수행하지 않는다")
        void createsRequestDeliveryJobAndInboxWithoutSending() {
            NotificationCreateResult result = service.create(command("event-1"));

            assertThat(result.duplicated()).isFalse();
            Long id = result.notification().id();
            NotificationDeliveryJob job = deliveryJobRepository.findById(id).orElseThrow();
            NotificationInbox inbox = inboxRepository.findById(id).orElseThrow();
            assertThat(requestRepository.count()).isEqualTo(1);
            assertThat(deliveryJobRepository.count()).isEqualTo(1);
            assertThat(inboxRepository.count()).isEqualTo(1);
            assertThat(job.getStatus()).isEqualTo(NotificationStatus.REQUESTED);
            assertThat(job.getRetryCount()).isZero();
            assertThat(job.getSentAt()).isNull();
            assertThat(inbox.getReadAt()).isNull();
        }

        @Test
        @DisplayName("생성 시각은 주입된 Clock 기준으로 기록한다")
        void createTimestampsUseInjectedClock() {
            NotificationCreateResult result = service.create(command("event-clock"));
            Long id = result.notification().id();

            NotificationDeliveryJob job = deliveryJobRepository.findById(id).orElseThrow();
            NotificationInbox inbox = inboxRepository.findById(id).orElseThrow();

            assertThat(result.notification().createdAt()).isEqualTo(FIXED_NOW);
            assertThat(result.notification().updatedAt()).isEqualTo(FIXED_NOW);
            assertThat(job.getCreatedAt()).isEqualTo(FIXED_NOW);
            assertThat(job.getUpdatedAt()).isEqualTo(FIXED_NOW);
            assertThat(inbox.getCreatedAt()).isEqualTo(FIXED_NOW);
            assertThat(inbox.getUpdatedAt()).isEqualTo(FIXED_NOW);
        }

        @Test
        @DisplayName("동일 이벤트 요청은 기존 요청을 반환한다")
        void duplicateEventReturnsExistingRequest() {
            NotificationCreateResult first = service.create(command("event-1"));
            NotificationCreateResult second = service.create(command("event-1"));

            assertThat(second.duplicated()).isTrue();
            assertThat(second.notification().id()).isEqualTo(first.notification().id());
            assertThat(requestRepository.count()).isEqualTo(1);
            assertThat(deliveryJobRepository.count()).isEqualTo(1);
            assertThat(inboxRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("요청 생성 command는 Bean Validation으로 검증한다")
        void invalidCommandIsValidated() {
            NotificationCreateCommand invalid = new NotificationCreateCommand(
                    "",
                    NotificationType.PAYMENT_CONFIRMED,
                    NotificationChannel.EMAIL,
                    "event-invalid",
                    "결제가 완료되었습니다",
                    "결제 확정 알림입니다.",
                    null,
                    null
            );

            assertThatThrownBy(() -> service.create(invalid))
                    .isInstanceOf(ConstraintViolationException.class)
                    .hasMessageContaining("recipientId is required");
        }

        @Test
        @DisplayName("템플릿 기반 생성은 렌더링된 title/message를 저장한다")
        void templateModeRendersAndPersistsConcreteContent() {
            templateRepository.saveAndFlush(new NotificationTemplate(
                    NotificationType.PAYMENT_CONFIRMED,
                    NotificationChannel.EMAIL,
                    "안녕하세요 ${userName}",
                    "금액 ${amount}",
                    true
            ));

            NotificationCreateResult result = service.create(new NotificationCreateCommand(
                    "user-1",
                    NotificationType.PAYMENT_CONFIRMED,
                    NotificationChannel.EMAIL,
                    "event-template",
                    null,
                    null,
                    Map.of("userName", "kim", "amount", "10000"),
                    null
            ));

            NotificationDetails details = service.get(result.notification().id());
            assertThat(details.title()).isEqualTo("안녕하세요 kim");
            assertThat(details.message()).isEqualTo("금액 10000");
        }

        @Test
        @DisplayName("직접 입력과 템플릿 변수를 함께 보내면 검증 에러가 난다")
        void mixedDirectAndTemplateModeIsRejected() {
            assertThatThrownBy(() -> service.create(new NotificationCreateCommand(
                    "user-1",
                    NotificationType.PAYMENT_CONFIRMED,
                    NotificationChannel.EMAIL,
                    "event-mixed",
                    "title",
                    "message",
                    Map.of("name", "kim"),
                    null
            )))
                    .isInstanceOf(NotificationValidationException.class)
                    .hasMessageContaining("provide either title/message or templateVariables");
        }

        @Test
        @DisplayName("잘못된 placeholder 문법 템플릿은 저장할 수 없다")
        void invalidPlaceholderTemplateIsRejected() {
            assertThatThrownBy(() -> templateService.create(
                    NotificationType.PAYMENT_CONFIRMED,
                    NotificationChannel.EMAIL,
                    new com.liveklass.demo.notification.dto.NotificationTemplateUpsertRequest(
                            "안녕하세요 ${userName} ${broken",
                            "본문",
                            true
                    )
            ))
                    .isInstanceOf(NotificationValidationException.class)
                    .hasMessageContaining("template contains unsupported placeholder syntax");
        }

        @Test
        @DisplayName("기존 요청이 있으면 템플릿 상태와 무관하게 중복 요청은 기존 row를 반환한다")
        void duplicateTemplateBackedRequestReturnsExistingBeforeTemplateRender() {
            NotificationCreateResult first = service.create(command("event-duplicate-template"));
            templateRepository.saveAndFlush(new NotificationTemplate(
                    NotificationType.PAYMENT_CONFIRMED,
                    NotificationChannel.EMAIL,
                    "안녕하세요 ${userName}",
                    "금액 ${amount}",
                    false
            ));

            NotificationCreateResult duplicate = service.create(new NotificationCreateCommand(
                    "user-1",
                    NotificationType.PAYMENT_CONFIRMED,
                    NotificationChannel.EMAIL,
                    "event-duplicate-template",
                    null,
                    null,
                    Map.of("userName", "kim", "amount", "10000"),
                    null
            ));

            assertThat(duplicate.duplicated()).isTrue();
            assertThat(duplicate.notification().id()).isEqualTo(first.notification().id());
        }
    }

    @Nested
    @DisplayName("읽음 처리")
    class ReadNotification {

        @Test
        @DisplayName("읽음 처리는 멱등이고 최초 읽음 시각을 보존한다")
        void markReadIsIdempotentAndPreservesFirstReadAt() {
            NotificationCreateResult created = service.create(command("event-read"));
            Long id = created.notification().id();

            NotificationDetails first = service.markRead(id, "user-1");
            NotificationDetails second = service.markRead(id, "user-1");

            assertThat(first.readAt()).isNotNull();
            assertThat(second.readAt()).isEqualTo(first.readAt());
            assertThat(service.listForRecipient("user-1", true)).hasSize(1);
            assertThat(service.listForRecipient("user-1", false)).isEmpty();
        }

        @Test
        @DisplayName("예약 알림은 즉시 inbox에 보이고 scheduledAt을 유지한다")
        void scheduledNotificationIsVisibleImmediatelyInInbox() {
            Instant scheduledAt = Instant.parse("2026-05-14T09:00:00Z");

            NotificationCreateResult result = service.create(new NotificationCreateCommand(
                    "user-1",
                    NotificationType.PAYMENT_CONFIRMED,
                    NotificationChannel.EMAIL,
                    "event-scheduled",
                    "예약 제목",
                    "예약 본문",
                    null,
                    scheduledAt
            ));

            NotificationDetails details = service.get(result.notification().id());
            assertThat(details.scheduledAt()).isEqualTo(scheduledAt);
            assertThat(service.listForRecipient("user-1", false)).hasSize(1);
        }

        @Test
        @DisplayName("목록 조회는 inbox와 delivery job을 한 번에 조회한다")
        void listForRecipientLoadsInboxAndDeliveryJobInSingleQuery() {
            service.create(command("event-list-1"));
            service.create(command("event-list-2"));
            service.markRead(service.create(command("event-list-3")).notification().id(), "user-1");
            Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
            statistics.clear();

            java.util.List<NotificationDetails> results = service.listForRecipient("user-1", null);

            assertThat(results).hasSize(3);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1L);
        }
    }

    private NotificationCreateCommand command(String eventId) {
        return new NotificationCreateCommand(
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

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }
}

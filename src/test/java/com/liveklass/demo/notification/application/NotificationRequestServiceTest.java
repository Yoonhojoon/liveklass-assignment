package com.liveklass.demo.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationRequest;
import com.liveklass.demo.notification.domain.NotificationStatus;
import com.liveklass.demo.notification.domain.NotificationType;
import com.liveklass.demo.notification.infrastructure.persistence.NotificationRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "notification.worker.enabled=false")
class NotificationRequestServiceTest {

    @Autowired
    private NotificationRequestService service;

    @Autowired
    private NotificationRequestRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void createsRequestedNotificationRequestWithoutSending() {
        NotificationCreateResult result = service.create(command("event-1"));

        assertThat(result.duplicated()).isFalse();
        NotificationRequest saved = repository.findById(result.notificationRequest().getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.REQUESTED);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getSentAt()).isNull();
    }

    @Test
    void duplicateEventReturnsExistingRequest() {
        NotificationCreateResult first = service.create(command("event-1"));
        NotificationCreateResult second = service.create(command("event-1"));

        assertThat(second.duplicated()).isTrue();
        assertThat(second.notificationRequest().getId()).isEqualTo(first.notificationRequest().getId());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void markReadIsIdempotentAndPreservesFirstReadAt() {
        NotificationCreateResult created = service.create(command("event-read"));

        NotificationRequest first = service.markRead(created.notificationRequest().getId(), "user-1");
        NotificationRequest second = service.markRead(created.notificationRequest().getId(), "user-1");

        assertThat(first.getReadAt()).isNotNull();
        assertThat(second.getReadAt()).isEqualTo(first.getReadAt());
        assertThat(service.listForRecipient("user-1", true)).hasSize(1);
        assertThat(service.listForRecipient("user-1", false)).isEmpty();
    }

    private NotificationCreateCommand command(String eventId) {
        return new NotificationCreateCommand(
                "user-1",
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                eventId,
                "결제가 완료되었습니다",
                "결제 확정 알림입니다."
        );
    }
}

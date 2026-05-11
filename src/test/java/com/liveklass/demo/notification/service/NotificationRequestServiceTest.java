package com.liveklass.demo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationDeliveryJob;
import com.liveklass.demo.notification.domain.NotificationInbox;
import com.liveklass.demo.notification.domain.NotificationStatus;
import com.liveklass.demo.notification.domain.NotificationType;
import com.liveklass.demo.notification.repository.NotificationDeliveryJobRepository;
import com.liveklass.demo.notification.repository.NotificationInboxRepository;
import com.liveklass.demo.notification.repository.NotificationRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "notification.worker.enabled=false")
class NotificationRequestServiceTest {

    @Autowired
    private NotificationRequestService service;

    @Autowired
    private NotificationRequestRepository requestRepository;

    @Autowired
    private NotificationDeliveryJobRepository deliveryJobRepository;

    @Autowired
    private NotificationInboxRepository inboxRepository;

    @BeforeEach
    void clean() {
        inboxRepository.deleteAll();
        deliveryJobRepository.deleteAll();
        requestRepository.deleteAll();
    }

    @Test
    void createsRequestDeliveryJobAndInboxWithoutSending() {
        NotificationCreateResult result = service.create(command("event-1"));

        assertThat(result.duplicated()).isFalse();
        Long id = result.notification().request().getId();
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
    void duplicateEventReturnsExistingRequest() {
        NotificationCreateResult first = service.create(command("event-1"));
        NotificationCreateResult second = service.create(command("event-1"));

        assertThat(second.duplicated()).isTrue();
        assertThat(second.notification().request().getId()).isEqualTo(first.notification().request().getId());
        assertThat(requestRepository.count()).isEqualTo(1);
        assertThat(deliveryJobRepository.count()).isEqualTo(1);
        assertThat(inboxRepository.count()).isEqualTo(1);
    }

    @Test
    void markReadIsIdempotentAndPreservesFirstReadAt() {
        NotificationCreateResult created = service.create(command("event-read"));
        Long id = created.notification().request().getId();

        NotificationDetails first = service.markRead(id, "user-1");
        NotificationDetails second = service.markRead(id, "user-1");

        assertThat(first.inbox().getReadAt()).isNotNull();
        assertThat(second.inbox().getReadAt()).isEqualTo(first.inbox().getReadAt());
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

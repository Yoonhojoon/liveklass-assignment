package com.liveklass.demo.notification.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationRequest;
import com.liveklass.demo.notification.domain.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class NotificationRequestRepositoryTest {

    @Autowired
    private NotificationRequestRepository repository;

    @Test
    void uniqueConstraintProtectsDuplicateEventIdentity() {
        repository.saveAndFlush(request("event-1"));

        assertThatThrownBy(() -> repository.saveAndFlush(request("event-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private NotificationRequest request(String eventId) {
        return new NotificationRequest(
                "user-1",
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                eventId,
                "title",
                "message"
        );
    }
}

package com.liveklass.demo.notification.repository;

import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationRequest;
import com.liveklass.demo.notification.domain.NotificationType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRequestRepository extends JpaRepository<NotificationRequest, Long> {

    Optional<NotificationRequest> findByRecipientIdAndNotificationTypeAndChannelAndEventId(
            String recipientId,
            NotificationType notificationType,
            NotificationChannel channel,
            String eventId
    );
}

package com.liveklass.demo.notification.dto;

import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationDeliveryJob;
import com.liveklass.demo.notification.domain.NotificationInbox;
import com.liveklass.demo.notification.domain.NotificationRequest;
import com.liveklass.demo.notification.domain.NotificationStatus;
import com.liveklass.demo.notification.domain.NotificationType;
import com.liveklass.demo.notification.service.NotificationDetails;
import java.time.Instant;

public record NotificationResponse(
        Long id,
        String recipientId,
        NotificationType notificationType,
        NotificationChannel channel,
        String eventId,
        String title,
        String message,
        NotificationStatus status,
        int retryCount,
        String lastFailureReason,
        Instant nextRetryAt,
        boolean read,
        Instant readAt,
        Instant createdAt,
        Instant updatedAt,
        Instant processingStartedAt,
        Instant sentAt,
        Instant failedAt
) {
    public static NotificationResponse from(NotificationDetails details) {
        NotificationRequest request = details.request();
        NotificationDeliveryJob deliveryJob = details.deliveryJob();
        NotificationInbox inbox = details.inbox();
        return new NotificationResponse(
                request.getId(),
                request.getRecipientId(),
                request.getNotificationType(),
                request.getChannel(),
                request.getEventId(),
                request.getTitle(),
                request.getMessage(),
                deliveryJob.getStatus(),
                deliveryJob.getRetryCount(),
                deliveryJob.getLastFailureReason(),
                deliveryJob.getNextRetryAt(),
                inbox.getReadAt() != null,
                inbox.getReadAt(),
                request.getCreatedAt(),
                deliveryJob.getUpdatedAt(),
                deliveryJob.getProcessingStartedAt(),
                deliveryJob.getSentAt(),
                deliveryJob.getFailedAt()
        );
    }
}

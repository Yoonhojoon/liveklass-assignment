package com.liveklass.demo.notification.service.dto;

import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationDeliveryJob;
import com.liveklass.demo.notification.domain.NotificationInbox;
import com.liveklass.demo.notification.domain.NotificationRequest;
import com.liveklass.demo.notification.domain.NotificationStatus;
import com.liveklass.demo.notification.domain.NotificationType;
import java.time.Instant;

public record NotificationDetails(
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
    public static NotificationDetails from(NotificationRequest request, NotificationDeliveryJob deliveryJob,
            NotificationInbox inbox) {
        Instant readAt = inbox.getReadAt();
        return new NotificationDetails(
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
                readAt != null,
                readAt,
                request.getCreatedAt(),
                deliveryJob.getUpdatedAt(),
                deliveryJob.getProcessingStartedAt(),
                deliveryJob.getSentAt(),
                deliveryJob.getFailedAt()
        );
    }
}

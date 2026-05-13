package com.liveklass.demo.notification.dto;

import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationStatus;
import com.liveklass.demo.notification.domain.NotificationType;
import com.liveklass.demo.notification.service.dto.NotificationDetails;
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
        return new NotificationResponse(
                details.id(),
                details.recipientId(),
                details.notificationType(),
                details.channel(),
                details.eventId(),
                details.title(),
                details.message(),
                details.status(),
                details.retryCount(),
                details.lastFailureReason(),
                details.nextRetryAt(),
                details.read(),
                details.readAt(),
                details.createdAt(),
                details.updatedAt(),
                details.processingStartedAt(),
                details.sentAt(),
                details.failedAt()
        );
    }
}

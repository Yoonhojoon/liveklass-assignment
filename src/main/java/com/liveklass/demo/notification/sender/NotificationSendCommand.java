package com.liveklass.demo.notification.sender;

import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationRequest;
import com.liveklass.demo.notification.domain.NotificationType;

public record NotificationSendCommand(
        Long id,
        String recipientId,
        NotificationType notificationType,
        NotificationChannel channel,
        String eventId,
        String title,
        String message
) {
    public static NotificationSendCommand from(NotificationRequest request) {
        return new NotificationSendCommand(
                request.getId(),
                request.getRecipientId(),
                request.getNotificationType(),
                request.getChannel(),
                request.getEventId(),
                request.getTitle(),
                request.getMessage()
        );
    }
}

package com.liveklass.demo.notification.api;

import com.liveklass.demo.notification.application.NotificationCreateCommand;
import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationType;

public record NotificationCreateRequest(
        String recipientId,
        NotificationType notificationType,
        NotificationChannel channel,
        String eventId,
        String title,
        String message
) {
    NotificationCreateCommand toCommand() {
        return new NotificationCreateCommand(recipientId, notificationType, channel, eventId, title, message);
    }
}

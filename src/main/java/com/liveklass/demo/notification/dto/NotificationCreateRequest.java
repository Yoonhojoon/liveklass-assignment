package com.liveklass.demo.notification.dto;

import com.liveklass.demo.notification.service.NotificationCreateCommand;
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
    public NotificationCreateCommand toCommand() {
        return new NotificationCreateCommand(recipientId, notificationType, channel, eventId, title, message);
    }
}

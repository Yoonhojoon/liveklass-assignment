package com.liveklass.demo.notification.dto;

import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationType;
import com.liveklass.demo.notification.service.dto.NotificationCreateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NotificationCreateRequest(
        @NotBlank(message = "recipientId is required")
        String recipientId,

        @NotNull(message = "notificationType is required")
        NotificationType notificationType,

        @NotNull(message = "channel is required")
        NotificationChannel channel,

        @NotBlank(message = "eventId is required")
        String eventId,

        @NotBlank(message = "title is required")
        String title,

        @NotBlank(message = "message is required")
        String message
) {
    public NotificationCreateCommand toCommand() {
        return new NotificationCreateCommand(recipientId, notificationType, channel, eventId, title, message);
    }
}

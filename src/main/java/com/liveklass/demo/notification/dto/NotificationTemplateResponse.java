package com.liveklass.demo.notification.dto;

import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationTemplate;
import com.liveklass.demo.notification.domain.NotificationType;
import java.time.Instant;

public record NotificationTemplateResponse(
        Long id,
        NotificationType notificationType,
        NotificationChannel channel,
        String titleTemplate,
        String messageTemplate,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public static NotificationTemplateResponse from(NotificationTemplate template) {
        return new NotificationTemplateResponse(
                template.getId(),
                template.getNotificationType(),
                template.getChannel(),
                template.getTitleTemplate(),
                template.getMessageTemplate(),
                template.isEnabled(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }
}

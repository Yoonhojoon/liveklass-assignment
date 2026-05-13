package com.liveklass.demo.notification.exception;

import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationType;

public class NotificationTemplateNotFoundException extends NotificationException {

    public NotificationTemplateNotFoundException(NotificationType notificationType, NotificationChannel channel) {
        super(NotificationErrorStatus.NOTIFICATION_TEMPLATE_NOT_FOUND,
                "Notification template not found: " + notificationType + "/" + channel);
    }
}

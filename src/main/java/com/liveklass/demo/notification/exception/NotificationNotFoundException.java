package com.liveklass.demo.notification.exception;

public class NotificationNotFoundException extends NotificationException {

    public NotificationNotFoundException(Long id) {
        super(NotificationErrorStatus.NOTIFICATION_NOT_FOUND, "Notification not found: " + id);
    }
}

package com.liveklass.demo.notification.exception;

public class NotificationValidationException extends NotificationException {

    public NotificationValidationException(String message) {
        super(NotificationErrorStatus.INVALID_NOTIFICATION_REQUEST, message);
    }
}

package com.liveklass.demo.notification.exception;

import com.liveklass.demo.common.exception.GeneralException;

public class NotificationValidationException extends GeneralException {

    public NotificationValidationException(String message) {
        super(NotificationErrorStatus.INVALID_NOTIFICATION_REQUEST, message);
    }
}

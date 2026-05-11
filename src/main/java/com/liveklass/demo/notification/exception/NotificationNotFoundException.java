package com.liveklass.demo.notification.exception;

import com.liveklass.demo.common.exception.GeneralException;

public class NotificationNotFoundException extends GeneralException {

    public NotificationNotFoundException(Long id) {
        super(NotificationErrorStatus.NOTIFICATION_NOT_FOUND, "Notification not found: " + id);
    }
}

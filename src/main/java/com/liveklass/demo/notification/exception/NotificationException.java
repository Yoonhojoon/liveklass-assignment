package com.liveklass.demo.notification.exception;

import com.liveklass.demo.common.exception.GeneralException;
import com.liveklass.demo.common.response.ErrorResponse;

public class NotificationException extends GeneralException {

    public NotificationException(ErrorResponse status) {
        super(status);
    }

    public NotificationException(ErrorResponse status, String message) {
        super(status, message);
    }
}

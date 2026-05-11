package com.liveklass.demo.notification.exception;

import com.liveklass.demo.common.response.ErrorResponse;
import org.springframework.http.HttpStatus;

public enum NotificationErrorStatus implements ErrorResponse {

    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION4004", "알림을 찾을 수 없습니다."),
    INVALID_NOTIFICATION_REQUEST(HttpStatus.BAD_REQUEST, "NOTIFICATION4000", "알림 요청이 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    NotificationErrorStatus(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}

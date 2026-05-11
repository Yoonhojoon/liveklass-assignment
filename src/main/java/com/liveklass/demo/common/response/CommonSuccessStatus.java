package com.liveklass.demo.common.response;

import org.springframework.http.HttpStatus;

public enum CommonSuccessStatus implements SuccessResponse {

    OK(HttpStatus.OK, "COMMON2000", "요청이 성공했습니다."),
    ACCEPTED(HttpStatus.ACCEPTED, "COMMON2020", "요청이 접수되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    CommonSuccessStatus(HttpStatus httpStatus, String code, String message) {
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

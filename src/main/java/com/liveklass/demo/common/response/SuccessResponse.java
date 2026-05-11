package com.liveklass.demo.common.response;

import org.springframework.http.HttpStatus;

public interface SuccessResponse {

    HttpStatus getHttpStatus();

    String getCode();

    String getMessage();
}

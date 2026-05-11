package com.liveklass.demo.common.exception;

import com.liveklass.demo.common.response.ErrorResponse;

public class GeneralException extends RuntimeException {

    private final ErrorResponse status;

    public GeneralException(ErrorResponse status) {
        super(status.getMessage());
        this.status = status;
    }

    public GeneralException(ErrorResponse status, String message) {
        super(message);
        this.status = status;
    }

    public ErrorResponse getStatus() {
        return status;
    }
}

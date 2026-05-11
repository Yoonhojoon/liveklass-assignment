package com.liveklass.demo.common.exception;

import com.liveklass.demo.common.response.CommonErrorStatus;
import com.liveklass.demo.common.response.ErrorResponse;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GeneralException.class)
    ResponseEntity<ProblemDetail> handleGeneralException(GeneralException exception) {
        return toProblemDetail(exception.getStatus(), exception.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class})
    ResponseEntity<ProblemDetail> handleBadRequest(Exception exception) {
        return toProblemDetail(CommonErrorStatus.BAD_REQUEST, exception.getMessage());
    }

    private ResponseEntity<ProblemDetail> toProblemDetail(ErrorResponse status, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status.getHttpStatus(), detail);
        problemDetail.setTitle(status.getMessage());
        problemDetail.setProperty("code", status.getCode());
        return ResponseEntity.status(status.getHttpStatus()).body(problemDetail);
    }
}

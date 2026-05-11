package com.liveklass.demo.notification.exception;

import com.liveklass.demo.common.response.ErrorResponse;
import com.liveklass.demo.notification.controller.NotificationController;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = NotificationController.class)
public class NotificationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("notification request is invalid");
        return toProblemDetail(NotificationErrorStatus.INVALID_NOTIFICATION_REQUEST, detail);
    }

    private ResponseEntity<ProblemDetail> toProblemDetail(ErrorResponse status, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status.getHttpStatus(), detail);
        problemDetail.setTitle(status.getMessage());
        problemDetail.setProperty("code", status.getCode());
        return ResponseEntity.status(status.getHttpStatus()).body(problemDetail);
    }
}

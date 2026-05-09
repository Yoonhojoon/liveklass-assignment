package com.liveklass.demo.notification.api;

import com.liveklass.demo.notification.application.NotificationNotFoundException;
import com.liveklass.demo.notification.application.NotificationValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class NotificationExceptionHandler {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NotificationNotFoundException.class)
    ErrorResponse notFound(NotificationNotFoundException exception) {
        return new ErrorResponse("NOT_FOUND", exception.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({NotificationValidationException.class, HttpMessageNotReadableException.class, MissingRequestHeaderException.class})
    ErrorResponse badRequest(Exception exception) {
        return new ErrorResponse("BAD_REQUEST", exception.getMessage());
    }
}

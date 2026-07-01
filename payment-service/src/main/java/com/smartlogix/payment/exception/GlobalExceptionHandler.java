package com.smartlogix.payment.exception;

import com.smartlogix.payment.client.OrderClientException;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderClientException.class)
    public ResponseEntity<ErrorResponse> handleOrderClientError(OrderClientException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse(OffsetDateTime.now(), HttpStatus.BAD_GATEWAY.value(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(OffsetDateTime.now(), HttpStatus.INTERNAL_SERVER_ERROR.value(), ex.getMessage()));
    }

    public record ErrorResponse(OffsetDateTime timestamp, int status, String message) {
    }
}

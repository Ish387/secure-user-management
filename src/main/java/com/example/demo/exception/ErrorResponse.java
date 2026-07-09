package com.example.demo.exception;

import java.time.LocalDateTime;

/** Standard error response body returned by all exception handlers. */
public record ErrorResponse(
        int status,
        String message,
        LocalDateTime timestamp
) {
}

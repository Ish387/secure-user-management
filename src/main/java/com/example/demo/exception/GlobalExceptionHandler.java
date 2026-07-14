package com.example.demo.exception;

import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Centralized exception handler for the entire application.
 * Catches specific exceptions and returns consistent, localized JSON error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, Locale locale) {
        String message = messageSource.getMessage(
                "error.auth.bad-credentials", null, "Invalid email or password", locale);

        ErrorResponse error = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                message,
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, Locale locale) {
        String entity = resolveNoun("entity", ex.getResourceName(), locale);
        String field = resolveNoun("field", ex.getFieldName(), locale);
        String message = messageSource.getMessage(
                "error.resource.not-found",
                new Object[]{entity, field, ex.getFieldValue()},
                String.format("%s not found with %s: '%s'", entity, field, ex.getFieldValue()),
                locale);

        ErrorResponse error = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                message,
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(DuplicateResourceException ex, Locale locale) {
        String entity = resolveNoun("entity", ex.getResourceName(), locale);
        String field = resolveNoun("field", ex.getFieldName(), locale);
        String message = messageSource.getMessage(
                "error.resource.already-exists",
                new Object[]{entity, field, ex.getFieldValue()},
                String.format("%s already exists with %s: '%s'", entity, field, ex.getFieldValue()),
                locale);

        ErrorResponse error = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                message,
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex, Locale locale) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> resolveNoun("field", fieldError.getField(), locale)
                        + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                message,
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, Locale locale) {
        String message = messageSource.getMessage(
                "error.generic", null, "An unexpected error occurred", locale);

        ErrorResponse error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                message,
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** Looks up "{prefix}.{rawWord}" (e.g. "field.email"), falling back to rawWord if no translation exists. */
    private String resolveNoun(String prefix, String rawWord, Locale locale) {
        String code = prefix + "." + rawWord.toLowerCase(Locale.ROOT);
        return messageSource.getMessage(code, null, rawWord, locale);
    }
}

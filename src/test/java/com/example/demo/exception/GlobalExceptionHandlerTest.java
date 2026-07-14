package com.example.demo.exception;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private static ResourceBundleMessageSource newMessageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(newMessageSource());

    @Test
    void handleResourceNotFound_Returns404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User", "id", 1L);

        ResponseEntity<ErrorResponse> response = handler.handleResourceNotFound(ex, Locale.ENGLISH);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().status());
        assertTrue(response.getBody().message().contains("User not found"));
    }

    @Test
    void handleResourceNotFound_Sinhala_ReturnsLocalizedMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User", "id", 1L);

        ResponseEntity<ErrorResponse> response =
                handler.handleResourceNotFound(ex, Locale.forLanguageTag("si"));

        assertTrue(response.getBody().message().contains("පරිශීලකයා"));
        assertTrue(response.getBody().message().contains("හමු නොවීය"));
    }

    @Test
    void handleDuplicateResource_Returns409() {
        DuplicateResourceException ex = new DuplicateResourceException("User", "email", "test@example.com");

        ResponseEntity<ErrorResponse> response = handler.handleDuplicateResource(ex, Locale.ENGLISH);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().status());
        assertTrue(response.getBody().message().contains("already exists"));
    }

    @Test
    void handleDuplicateResource_Sinhala_ReturnsLocalizedMessage() {
        DuplicateResourceException ex = new DuplicateResourceException("User", "email", "test@example.com");

        ResponseEntity<ErrorResponse> response =
                handler.handleDuplicateResource(ex, Locale.forLanguageTag("si"));

        assertTrue(response.getBody().message().contains("පරිශීලකයා"));
        assertTrue(response.getBody().message().contains("දැනටමත් පවතී"));
    }

    @Test
    void handleBadCredentials_Returns401() {
        BadCredentialsException ex = new BadCredentialsException("Bad credentials");

        ResponseEntity<ErrorResponse> response = handler.handleBadCredentials(ex, Locale.ENGLISH);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(401, response.getBody().status());
        assertEquals("Invalid email or password", response.getBody().message());
    }

    @Test
    void handleBadCredentials_Sinhala_ReturnsLocalizedMessage() {
        BadCredentialsException ex = new BadCredentialsException("Bad credentials");

        ResponseEntity<ErrorResponse> response =
                handler.handleBadCredentials(ex, Locale.forLanguageTag("si"));

        assertEquals("වලංගු නොවන විද්‍යුත් තැපෑල හෝ මුරපදය", response.getBody().message());
    }

    @Test
    void handleValidationErrors_Returns400() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "object");
        bindingResult.addError(new FieldError("object", "email", "Email is required"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidationErrors(ex, Locale.ENGLISH);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().status());
        assertTrue(response.getBody().message().contains("email"));
    }

    @Test
    void handleGenericException_Returns500() {
        Exception ex = new RuntimeException("Something went wrong");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex, Locale.ENGLISH);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().status());
        assertEquals("An unexpected error occurred", response.getBody().message());
    }

    @Test
    void handleGenericException_Sinhala_ReturnsLocalizedMessage() {
        Exception ex = new RuntimeException("Something went wrong");

        ResponseEntity<ErrorResponse> response =
                handler.handleGenericException(ex, Locale.forLanguageTag("si"));

        assertEquals("අනපේක්ෂිත දෝෂයක් සිදු විය", response.getBody().message());
    }
}

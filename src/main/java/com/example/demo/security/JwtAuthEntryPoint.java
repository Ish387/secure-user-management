package com.example.demo.security;

import com.example.demo.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Handles unauthorized access attempts — returns a JSON 401 response
 * instead of Spring Security's default redirect to a login page.
 *
 * Runs inside the Spring Security filter chain, before DispatcherServlet, so Spring MVC's
 * Accept-Language-driven LocaleResolver isn't populated yet here; locale is resolved directly
 * from the servlet request instead.
 */
@Component
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;

    public JwtAuthEntryPoint(MessageSource messageSource, ObjectMapper objectMapper) {
        this.messageSource = messageSource;
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        Locale locale = request.getLocale();
        String message = messageSource.getMessage("error.unauthorized", null, "Unauthorized", locale);

        ErrorResponse error = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                message,
                LocalDateTime.now());

        objectMapper.writeValue(response.getWriter(), error);
    }
}

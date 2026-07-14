package com.example.demo.security;

import com.example.demo.config.SecurityConfig;
import com.example.demo.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP fixed-window rate limiter for /api/auth/** (login/register), guarding against
 * brute-force and credential-stuffing. Counters are held in memory and periodically purged.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final int maxRequests;
    private final long windowMs;
    private final boolean trustForwardedHeader;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MessageSource messageSource;

    private final ConcurrentHashMap<String, RequestCounter> countersByIp = new ConcurrentHashMap<>();

    public RateLimitingFilter(
            @Value("${rate-limit.auth.max-requests}") int maxRequests,
            @Value("${rate-limit.auth.window-ms}") long windowMs,
            @Value("${rate-limit.trust-forwarded-header:false}") boolean trustForwardedHeader,
            ObjectMapper objectMapper,
            Clock clock,
            MessageSource messageSource) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
        this.trustForwardedHeader = trustForwardedHeader;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.messageSource = messageSource;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !PATH_MATCHER.match(SecurityConfig.AUTH_PATH_PATTERN, request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String ip = resolveClientIp(request);
        long now = clock.millis();

        RequestCounter counter = countersByIp.compute(ip, (key, existing) -> {
            if (existing == null || now - existing.windowStart >= windowMs) {
                return new RequestCounter(now, 1);
            }
            existing.count++;
            return existing;
        });

        if (counter.count > maxRequests) {
            long retryAfterSeconds = Math.max(
                    1, (long) Math.ceil((counter.windowStart + windowMs - now) / 1000.0));
            writeTooManyRequests(request, response, retryAfterSeconds);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (trustForwardedHeader) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwardedFor)) {
                return forwardedFor.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(
            HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Locale locale = request.getLocale();
        String message = messageSource.getMessage(
                "error.rate-limit.exceeded", null, "Too many requests. Please try again later.", locale);

        ErrorResponse error = new ErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                message,
                LocalDateTime.now(clock));

        objectMapper.writeValue(response.getWriter(), error);
    }

    /** Evicts counters whose window expired well in the past, bounding memory growth. */
    @Scheduled(fixedRateString = "${rate-limit.cleanup-interval-ms:300000}")
    void purgeExpiredCounters() {
        long now = clock.millis();
        countersByIp.entrySet().removeIf(entry -> now - entry.getValue().windowStart > windowMs * 2);
    }

    private static final class RequestCounter {
        private final long windowStart;
        private volatile int count;

        private RequestCounter(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}

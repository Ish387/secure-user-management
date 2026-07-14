package com.example.demo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.*;

class RateLimitingFilterTest {

    private static final int MAX_REQUESTS = 3;
    private static final long WINDOW_MS = 1000;

    private MutableClock clock;
    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        filter = new RateLimitingFilter(MAX_REQUESTS, WINDOW_MS, false, newObjectMapper(), clock, newMessageSource());
    }

    @Test
    void withinLimit_AllRequestsPassThrough() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            FilterChain chain = mock(FilterChain.class);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(authRequest("1.2.3.4"), response, chain);

            verify(chain, times(1)).doFilter(any(), any());
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    void exceedingLimit_Returns429WithRetryAfterAndBody() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            filter.doFilter(authRequest("1.2.3.4"), new MockHttpServletResponse(), mock(FilterChain.class));
        }

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(authRequest("1.2.3.4"), response, chain);

        verify(chain, times(0)).doFilter(any(), any());
        assertEquals(429, response.getStatus());
        assertNotNull(response.getHeader("Retry-After"));
        assertTrue(Long.parseLong(response.getHeader("Retry-After")) > 0);

        ErrorBody body = newObjectMapper().readValue(response.getContentAsString(), ErrorBody.class);
        assertEquals(429, body.status);
        assertNotNull(body.message);
    }

    @Test
    void afterWindowExpires_CounterResets() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            filter.doFilter(authRequest("1.2.3.4"), new MockHttpServletResponse(), mock(FilterChain.class));
        }

        clock.advance(Duration.ofMillis(WINDOW_MS + 1));

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(authRequest("1.2.3.4"), response, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertEquals(200, response.getStatus());
    }

    @Test
    void differentIps_HaveIndependentQuotas() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            filter.doFilter(authRequest("1.2.3.4"), new MockHttpServletResponse(), mock(FilterChain.class));
        }

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(authRequest("5.6.7.8"), response, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertEquals(200, response.getStatus());
    }

    @Test
    void nonAuthPath_AlwaysPassesThroughRegardlessOfVolume() throws Exception {
        for (int i = 0; i < MAX_REQUESTS + 5; i++) {
            FilterChain chain = mock(FilterChain.class);
            MockHttpServletResponse response = new MockHttpServletResponse();

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
            request.setRemoteAddr("1.2.3.4");

            filter.doFilter(request, response, chain);

            verify(chain, times(1)).doFilter(any(), any());
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    void forwardedForHeader_IgnoredByDefault() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            MockHttpServletRequest request = authRequest("1.2.3.4");
            request.addHeader("X-Forwarded-For", "9.9.9.9");
            filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        }

        // Same remoteAddr, different spoofed X-Forwarded-For each time -> still throttled as one IP
        MockHttpServletRequest request = authRequest("1.2.3.4");
        request.addHeader("X-Forwarded-For", "8.8.8.8");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));

        assertEquals(429, response.getStatus());
    }

    @Test
    void forwardedForHeader_HonoredWhenTrusted() throws Exception {
        RateLimitingFilter trustingFilter =
                new RateLimitingFilter(MAX_REQUESTS, WINDOW_MS, true, newObjectMapper(), clock, newMessageSource());

        for (int i = 0; i < MAX_REQUESTS; i++) {
            MockHttpServletRequest request = authRequest("1.2.3.4");
            request.addHeader("X-Forwarded-For", "9.9.9.9, 1.1.1.1");
            trustingFilter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        }

        // Same remoteAddr but a different forwarded IP -> independent quota
        MockHttpServletRequest request = authRequest("1.2.3.4");
        request.addHeader("X-Forwarded-For", "7.7.7.7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        trustingFilter.doFilter(request, response, mock(FilterChain.class));

        assertEquals(200, response.getStatus());
    }

    @Test
    void purgeExpiredCounters_RemovesStaleEntries() throws Exception {
        filter.doFilter(authRequest("1.2.3.4"), new MockHttpServletResponse(), mock(FilterChain.class));

        clock.advance(Duration.ofMillis(WINDOW_MS * 2 + 1));
        filter.purgeExpiredCounters();

        // If the entry was purged, this IP gets a fresh window and all MAX_REQUESTS succeed again
        for (int i = 0; i < MAX_REQUESTS; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(authRequest("1.2.3.4"), response, mock(FilterChain.class));
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    void exceedingLimit_SinhalaAcceptLanguage_ReturnsLocalizedMessage() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            filter.doFilter(authRequest("1.2.3.4"), new MockHttpServletResponse(), mock(FilterChain.class));
        }

        MockHttpServletRequest request = authRequest("1.2.3.4");
        request.addPreferredLocale(java.util.Locale.forLanguageTag("si"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));

        assertEquals(429, response.getStatus());
        ErrorBody body = newObjectMapper().readValue(response.getContentAsString(), ErrorBody.class);
        assertTrue(body.message.contains("ඉල්ලීම්"));
    }

    private MockHttpServletRequest authRequest(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    /** Mirrors Spring Boot's autoconfigured ObjectMapper: JavaTimeModule + ISO-8601 dates, not arrays. */
    private static ObjectMapper newObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static MessageSource newMessageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }

    private record ErrorBody(int status, String message, String timestamp) {
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

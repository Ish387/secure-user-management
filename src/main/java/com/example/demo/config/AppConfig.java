package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * General-purpose application beans not tied to a specific concern (security, web, etc.).
 */
@Configuration
public class AppConfig {

    /** Clock used across the app for rate-limit windows and timestamps; substitutable in tests. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

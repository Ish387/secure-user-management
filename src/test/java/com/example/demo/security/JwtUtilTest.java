package com.example.demo.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    // Base64-encoded test secret (same as application.properties)
    private static final String TEST_SECRET = "dGhpcyBpcyBhIHZlcnkgc2VjdXJlIGtleSBmb3Igand0IHNpZ25pbmc=";
    private static final long EXPIRATION_MS = 900000; // 15 minutes

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_SECRET, EXPIRATION_MS);
    }

    @Test
    void generateToken_ContainsCorrectClaims() {
        String token = jwtUtil.generateToken(1L, "john@example.com");

        assertNotNull(token);
        assertFalse(token.isEmpty());
        // JWT has 3 parts separated by dots
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void extractEmail_ReturnsCorrectEmail() {
        String token = jwtUtil.generateToken(1L, "john@example.com");

        String email = jwtUtil.extractEmail(token);

        assertEquals("john@example.com", email);
    }

    @Test
    void extractUserId_ReturnsCorrectId() {
        String token = jwtUtil.generateToken(42L, "john@example.com");

        Long userId = jwtUtil.extractUserId(token);

        assertEquals(42L, userId);
    }

    @Test
    void isTokenValid_ValidToken_ReturnsTrue() {
        String token = jwtUtil.generateToken(1L, "john@example.com");

        assertTrue(jwtUtil.isTokenValid(token));
    }

    @Test
    void isTokenValid_ExpiredToken_ReturnsFalse() {
        // Create a JwtUtil with 0ms expiration so token is already expired
        JwtUtil expiredJwtUtil = new JwtUtil(TEST_SECRET, 0);
        String token = expiredJwtUtil.generateToken(1L, "john@example.com");

        assertFalse(jwtUtil.isTokenValid(token));
    }

    @Test
    void isTokenValid_TamperedToken_ReturnsFalse() {
        String token = jwtUtil.generateToken(1L, "john@example.com");
        // Tamper with the token by modifying the last character
        String tampered = token.substring(0, token.length() - 1) + "X";

        assertFalse(jwtUtil.isTokenValid(tampered));
    }

    @Test
    void isTokenValid_GarbageString_ReturnsFalse() {
        assertFalse(jwtUtil.isTokenValid("not.a.jwt"));
    }
}

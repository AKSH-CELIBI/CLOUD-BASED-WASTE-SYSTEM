package com.example.wallet.auth;

import com.example.wallet.config.JwtProperties;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private static final String SECRET = "local-test-secret-that-is-at-least-32-bytes-long";

    @Test
    void issuesAndReadsSignedToken() {
        JwtService service = new JwtService(new JwtProperties(SECRET, 3600));

        String token = service.issue("user@example.com");

        assertEquals("user@example.com", service.subject(token));
    }

    @Test
    void rejectsTamperedToken() {
        JwtService service = new JwtService(new JwtProperties(SECRET, 3600));
        String token = service.issue("user@example.com");
        String tampered = token.substring(0, token.length() - 2) + "aa";

        assertThrows(JwtException.class, () -> service.subject(tampered));
    }

    @Test
    void rejectsShortSecretAtStartup() {
        assertThrows(IllegalStateException.class, () -> new JwtProperties("too-short", 3600));
    }

    @Test
    void rejectsExpiredToken() {
        JwtService service = new JwtService(new JwtProperties(SECRET, 3600));
        String expired = io.jsonwebtoken.Jwts.builder()
                .subject("user@example.com")
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        assertThrows(JwtException.class, () -> service.subject(expired));
    }
}

package com.aimall.common;

import com.aimall.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTest {
    private static final String SECRET = "unit-test-secret-key-that-is-long-enough-0123456789";

    @Test
    void issueAndParseRoundTrip() {
        String token = JwtUtil.issue(SECRET, 42L, "alice", 60);
        Claims claims = JwtUtil.parse(SECRET, token);
        assertEquals("42", claims.getSubject());
        assertEquals("alice", claims.get("username"));
    }

    @Test
    void wrongSecretIsRejected() {
        String token = JwtUtil.issue(SECRET, 1L, "bob", 60);
        assertThrows(JwtException.class, () -> JwtUtil.parse("another-secret-key-that-is-long-enough-9876543210", token));
    }

    @Test
    void expiredTokenIsRejected() {
        String token = JwtUtil.issue(SECRET, 1L, "bob", -10);
        assertThrows(JwtException.class, () -> JwtUtil.parse(SECRET, token));
    }
}

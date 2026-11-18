package com.aimall.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;

/** HS256 JWT: user-service 签发, gateway 校验。secret 至少 32 字节。 */
public final class JwtUtil {

    private JwtUtil() {
    }

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public static String issue(String secret, long userId, String username, long ttlSeconds) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlSeconds * 1000))
                .signWith(key(secret))
                .compact();
    }

    /** 校验失败会抛出 JwtException。 */
    public static Claims parse(String secret, String token) {
        return Jwts.parser().verifyWith(key(secret)).build().parseSignedClaims(token).getPayload();
    }
}

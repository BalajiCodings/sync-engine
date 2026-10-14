package com.balaji.sync_engine.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret:}") String configuredSecret,
            @Value("${jwt.expiration-ms:900000}") long expirationMs) {
        String secret = (configuredSecret != null && !configuredSecret.isBlank())
                ? configuredSecret
                : System.getenv("JWT_SECRET");

        // Safe fallback for local development/testing; enforces 256 bits (32 bytes)
        if (secret == null || secret.length() < 32) {
            secret = "sync-engine-default-development-secret-key-must-be-at-least-256-bits-long-32bytes";
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("role", user.getRole().name())
                .claim("deviceId", user.getDeviceId())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractDeviceId(String token) {
        return parseClaims(token).get("deviceId", String.class);
    }

    public boolean isTokenValid(String token, String expectedUsername) {
        try {
            String username = extractUsername(token);
            return username.equals(expectedUsername) && !isExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(parseClaims(token));
    }
}
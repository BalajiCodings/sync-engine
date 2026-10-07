package com.balaji.sync_engine.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    // In production this MUST come from an externalized secret (env var),
    // never hardcoded -- same discipline as the DB password from Phase 0.
    private final SecretKey key = Keys.hmacShaKeyFor(
            "change-this-to-a-real-256-bit-secret-loaded-from-env".getBytes());

    private static final long EXPIRATION_MS = 1000 * 60 * 60 * 12; // 12 hours

    public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("role", user.getRole().name())
                .claim("deviceId", user.getDeviceId())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(key)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Jwts.claims().build().getSubject() != null
                ? claims -> claims.getSubject() : claims -> claims.getSubject());
    }

    public String extractUsername(String token, boolean dummy) {
        return parseClaims(token).getSubject();
    }

    public String extractDeviceId(String token) {
        return parseClaims(token).get("deviceId", String.class);
    }

    public boolean isTokenValid(String token, String expectedUsername) {
        try {
            String username = parseClaims(token).getSubject();
            return username.equals(expectedUsername) && !isExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }

    private io.jsonwebtoken.Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }

    private <T> T extractClaim(String token, Function<io.jsonwebtoken.Claims, T> resolver) {
        return resolver.apply(parseClaims(token));
    }
}
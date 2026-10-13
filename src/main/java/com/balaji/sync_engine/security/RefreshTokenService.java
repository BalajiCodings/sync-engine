package com.balaji.sync_engine.security;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class RefreshTokenService {

    public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public RefreshToken createRefreshToken(User user) {
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setToken(generateRandomToken());
        rt.setExpiresAt(Instant.now().plus(REFRESH_TOKEN_TTL));
        rt.setRevoked(false);
        return refreshTokenRepository.save(rt);
    }

    /**
     * Rotates the refresh token:
     * If a revoked token is re-used, all tokens for the user are revoked immediately (reuse detection).
     */
    @Transactional
    public RefreshToken rotateRefreshToken(String rawToken) {
        RefreshToken existing = refreshTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (existing.isRevoked()) {
            // Possible token theft/replay attack! Invalidate all tokens for this user.
            refreshTokenRepository.revokeAllForUser(existing.getUser().getId());
            throw new IllegalArgumentException("Refresh token was previously revoked. Suspicious activity detected. All sessions revoked.");
        }

        if (existing.isExpired()) {
            throw new IllegalArgumentException("Refresh token has expired. Please login again.");
        }

        // Revoke the old token
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        // Issue a new token for this user
        return createRefreshToken(existing.getUser());
    }

    @Transactional
    public void revokeToken(String rawToken) {
        refreshTokenRepository.findByToken(rawToken).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    private String generateRandomToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
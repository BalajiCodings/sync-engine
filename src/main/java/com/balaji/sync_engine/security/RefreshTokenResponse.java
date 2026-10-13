package com.balaji.sync_engine.security;

public record RefreshTokenResponse(
        String accessToken,
        String refreshToken
) {}
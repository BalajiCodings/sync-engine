package com.balaji.sync_engine.security;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String role,
        String deviceId
) {}
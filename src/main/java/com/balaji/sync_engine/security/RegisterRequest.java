package com.balaji.sync_engine.security;

public record RegisterRequest(
        String username,
        String password,
        Role role,
        String deviceId   // required for FIELD_WORKER, null otherwise
) {}
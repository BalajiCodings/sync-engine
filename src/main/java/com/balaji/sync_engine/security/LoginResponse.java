package com.balaji.sync_engine.security;

public record LoginResponse(String token, String role, String deviceId) {}
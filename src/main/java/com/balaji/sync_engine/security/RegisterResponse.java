package com.balaji.sync_engine.security;

import java.util.UUID;

public record RegisterResponse(UUID userId, String username, String role) {}
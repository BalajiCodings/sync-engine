package com.balaji.sync_engine.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record StreamTicketResponse(
        @Schema(description = "Single-use ticket. Pass it as ?ticket= when opening /api/sync/stream.")
        String ticket,
        @Schema(description = "Seconds until the ticket expires")
        long expiresInSeconds
) {}
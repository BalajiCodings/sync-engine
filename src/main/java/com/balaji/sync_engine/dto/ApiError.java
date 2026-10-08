package com.balaji.sync_engine.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ApiError(
        @Schema(description = "Stable machine-readable code. Frontends should switch on this, never on message text.")
        ErrorCode code,
        @Schema(description = "Human-readable explanation, safe to display to a user.")
        String message
) {}
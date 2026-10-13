package com.balaji.sync_engine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SyncPullRequest(
        @NotBlank(message = "deviceId is required")
        String deviceId,

        @Schema(description = "Position in server log. Send null on first sync, then nextCursor.")
        @Min(value = 0, message = "cursor must not be negative")
        Long cursor,

        @Schema(description = "Max events to return (default 500, max 1000)")
        @Min(value = 1, message = "limit must be at least 1")
        @Max(value = 1000, message = "limit cannot exceed 1000")
        Integer limit
) {}
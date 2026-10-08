package com.balaji.sync_engine.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SyncPullRequest(
        String deviceId,
        @Schema(description = "Last known server checkpoint HLC, or null for a first-time sync")
        String lastSyncedHlc,
        @Schema(description = "Max events to return (default 500, max 1000)")
        Integer limit
) {}
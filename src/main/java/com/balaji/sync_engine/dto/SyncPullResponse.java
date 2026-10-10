package com.balaji.sync_engine.dto;

import com.balaji.sync_engine.entity.ChangeEvent;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record SyncPullResponse(
        List<ChangeEvent> changes,
        @Schema(description = "Send this back as cursor on the next pull.")
        long nextCursor,
        @Schema(description = "True if more changes remain. Pull again immediately.")
        boolean hasMore,
        @Schema(description = "Server's hybrid logical clock. Devices fold it into their own clock. "
                + "NOT a pull position.")
        String serverHlc
) {}
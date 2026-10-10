package com.balaji.sync_engine.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SyncPullRequest(
        String deviceId,
        @Schema(description = "Position in the server's event log. Send null on a device's first sync; "
                + "afterwards send back the nextCursor from the previous pull.")
        Long cursor,
        @Schema(description = "Max events to return (default 500, max 1000)")
        Integer limit
) {}
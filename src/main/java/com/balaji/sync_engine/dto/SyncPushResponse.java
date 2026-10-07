package com.balaji.sync_engine.dto;

import java.util.List;

public record SyncPushResponse(
        int acceptedCount,
        List<ConflictInfo> conflicts,
        List<RejectedEvent> rejectedEvents,
        String newCheckpointHlc
) {}
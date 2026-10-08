package com.balaji.sync_engine.dto;

import com.balaji.sync_engine.entity.ChangeEvent;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record SyncPullResponse(
        List<ChangeEvent> changes,
        String newCheckpointHlc,
        @Schema(description = "True if more changes remain beyond this response -- "
                + "call pull again immediately with the returned checkpoint")
        boolean hasMore
) {}
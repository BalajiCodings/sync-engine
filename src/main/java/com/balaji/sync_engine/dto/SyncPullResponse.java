package com.balaji.sync_engine.dto;

import com.balaji.sync_engine.entity.ChangeEvent;

import java.util.List;

public record SyncPullResponse(
        List<ChangeEvent> changes,
        String newCheckpointHlc
) {}
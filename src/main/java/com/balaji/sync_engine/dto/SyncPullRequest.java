package com.balaji.sync_engine.dto;

public record SyncPullRequest(
        String deviceId,
        String lastSyncedHlc
) {}
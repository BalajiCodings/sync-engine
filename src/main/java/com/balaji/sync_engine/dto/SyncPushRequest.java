package com.balaji.sync_engine.dto;

import com.balaji.sync_engine.entity.ChangeEvent;

import java.util.List;

public record SyncPushRequest(
        String deviceId,
        List<ChangeEvent> changes
) {}
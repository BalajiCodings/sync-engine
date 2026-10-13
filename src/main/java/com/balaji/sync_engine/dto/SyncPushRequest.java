package com.balaji.sync_engine.dto;

import com.balaji.sync_engine.entity.ChangeEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SyncPushRequest(
        @NotBlank(message = "deviceId is required")
        String deviceId,

        @NotEmpty(message = "changes list cannot be empty")
        @Size(max = 200, message = "Cannot push more than 200 events in a single batch")
        List<ChangeEvent> changes
) {}
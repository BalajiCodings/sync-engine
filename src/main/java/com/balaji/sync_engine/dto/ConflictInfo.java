package com.balaji.sync_engine.dto;

import com.balaji.sync_engine.conflict.ConflictType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record ConflictInfo(
        ConflictType conflictType,
        String entityType,
        UUID entityId,
        UUID eventId,
        @Schema(description = "Null for DELETE_UPDATE_CONFLICT, which has no single field")
        String fieldName,
        String currentValue,
        String currentDeviceId,
        String incomingValue,
        String incomingDeviceId,
        String message
) {}
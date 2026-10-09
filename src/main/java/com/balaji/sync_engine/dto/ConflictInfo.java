package com.balaji.sync_engine.dto;

import com.balaji.sync_engine.conflict.ConflictType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record ConflictInfo(
        @Schema(description = "ID of the persisted review case; pass it to POST /api/conflicts/{id}/resolve")
        UUID conflictId,
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
) {
    /** Detection-time constructor: a conflict has no ID until it is persisted. */
    public ConflictInfo(ConflictType conflictType, String entityType, UUID entityId, UUID eventId,
                        String fieldName, String currentValue, String currentDeviceId,
                        String incomingValue, String incomingDeviceId, String message) {
        this(null, conflictType, entityType, entityId, eventId, fieldName, currentValue,
                currentDeviceId, incomingValue, incomingDeviceId, message);
    }

    public ConflictInfo withConflictId(UUID id) {
        return new ConflictInfo(id, conflictType, entityType, entityId, eventId, fieldName,
                currentValue, currentDeviceId, incomingValue, incomingDeviceId, message);
    }
}
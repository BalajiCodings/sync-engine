package com.balaji.sync_engine.dto;

import com.balaji.sync_engine.conflict.*;
import com.balaji.sync_engine.entity.ConflictRecord;

import java.time.Instant;
import java.util.UUID;

public record ConflictView(
        UUID id, ConflictType conflictType, String entityType, UUID entityId, UUID eventId,
        String fieldName, String currentValue, String currentDeviceId,
        String incomingValue, String incomingDeviceId, String message,
        ConflictStatus status, ResolutionAction resolution, String resolvedValue,
        UUID resolutionEventId, String resolvedBy, Instant resolvedAt, Instant detectedAt
) {
    public static ConflictView from(ConflictRecord r) {
        return new ConflictView(r.getId(), r.getConflictType(), r.getEntityType(), r.getEntityId(),
                r.getEventId(), r.getFieldName(), r.getCurrentValue(), r.getCurrentDeviceId(),
                r.getIncomingValue(), r.getIncomingDeviceId(), r.getMessage(), r.getStatus(),
                r.getResolution(), r.getResolvedValue(), r.getResolutionEventId(),
                r.getResolvedBy(), r.getResolvedAt(), r.getDetectedAt());
    }
}
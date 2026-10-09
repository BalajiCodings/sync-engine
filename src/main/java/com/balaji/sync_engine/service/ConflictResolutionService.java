package com.balaji.sync_engine.service;

import com.balaji.sync_engine.conflict.MergeInput;

import com.balaji.sync_engine.conflict.MergeOutcome;
import com.balaji.sync_engine.dto.ConflictInfo;
import com.balaji.sync_engine.conflict.ConflictType;
import com.balaji.sync_engine.conflict.MergeStrategyRegistry;
import com.balaji.sync_engine.entity.FieldState;
import com.balaji.sync_engine.repository.FieldStateRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;


import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ConflictResolutionService {

    private final FieldStateRepository fieldStateRepository;
    private final MergeStrategyRegistry strategyRegistry;
    private final JsonMapper jsonMapper;

    public ConflictResolutionService(FieldStateRepository fieldStateRepository,
                                      MergeStrategyRegistry strategyRegistry,
                                      JsonMapper jsonMapper) {
        this.fieldStateRepository = fieldStateRepository;
        this.strategyRegistry = strategyRegistry;
        this.jsonMapper = jsonMapper;
    }
    private static final Set<String> UNTRACKED_FIELDS = Set.of("id", "createdAt", "updatedAt", "deletedAt");

    private boolean isTrackedField(String fieldName) {
        return !UNTRACKED_FIELDS.contains(fieldName);
    }

    public record ApplyResult(List<ConflictInfo> conflicts) {}

    public ApplyResult applyIncomingChange(UUID eventId, String entityType, UUID entityId, String payloadJson,
                                            String incomingHlc, String deviceId) {
        List<ConflictInfo> conflicts = new ArrayList<>();
        JsonNode payload = jsonMapper.readTree(payloadJson);

        payload.properties().forEach(entry -> {
            String fieldName = entry.getKey();
            JsonNode valueNode = entry.getValue();

            if (!isTrackedField(fieldName)) {
                return;
            }

            String incomingValue = valueNode.isNull() ? null : valueNode.asString();

            Optional<FieldState> existing = fieldStateRepository
                    .findByEntityTypeAndEntityIdAndFieldName(entityType, entityId, fieldName);

            if (existing.isEmpty()) {
                saveFieldState(entityType, entityId, fieldName, incomingValue, incomingHlc, deviceId);
                return;
            }

            FieldState current = existing.get();
            MergeInput input = new MergeInput(fieldName, current.getFieldValue(), current.getLastWriteHlc(),
                    current.getLastWriteDeviceId(), incomingValue, incomingHlc, deviceId);

            MergeOutcome outcome = strategyRegistry.strategyFor(fieldName).merge(input);

            if (outcome.conflict()) {
                conflicts.add(new ConflictInfo(
                        ConflictType.FIELD_CONFLICT, entityType, entityId, eventId, fieldName,
                        current.getFieldValue(), current.getLastWriteDeviceId(),
                        incomingValue, deviceId,
                        outcome.reason()
                ));
                return;
            }

            current.setFieldValue(outcome.resolvedValue());
            current.setLastWriteHlc(incomingHlc);
            current.setLastWriteDeviceId(deviceId);
            fieldStateRepository.save(current);
        });

        return new ApplyResult(conflicts);
    }


    private void saveFieldState(String entityType, UUID entityId, String fieldName,
                                 String value, String hlc, String deviceId) {
        FieldState state = new FieldState();
        state.setEntityType(entityType);
        state.setEntityId(entityId);
        state.setFieldName(fieldName);
        state.setFieldValue(value);
        state.setLastWriteHlc(hlc);
        state.setLastWriteDeviceId(deviceId);
        fieldStateRepository.save(state);
    }
    /** Writes field state unconditionally, with no merge strategy. Used for human-made decisions. */
    public void applyAuthoritative(String entityType, UUID entityId, String payloadJson,
                                   String hlc, String deviceId) {
        JsonNode payload = jsonMapper.readTree(payloadJson);

        payload.properties().forEach(entry -> {
            String fieldName = entry.getKey();
            if (!isTrackedField(fieldName)) {
                return;
            }
            JsonNode valueNode = entry.getValue();
            String value = valueNode.isNull() ? null : valueNode.asString();

            Optional<FieldState> existing = fieldStateRepository
                    .findByEntityTypeAndEntityIdAndFieldName(entityType, entityId, fieldName);

            if (existing.isEmpty()) {
                saveFieldState(entityType, entityId, fieldName, value, hlc, deviceId);
                return;
            }

            FieldState state = existing.get();
            state.setFieldValue(value);
            state.setLastWriteHlc(hlc);
            state.setLastWriteDeviceId(deviceId);
            fieldStateRepository.save(state);
        });
    }
}
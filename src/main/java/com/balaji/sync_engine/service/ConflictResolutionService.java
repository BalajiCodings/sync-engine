package com.balaji.sync_engine.service;

import com.balaji.sync_engine.conflict.MergeInput;
import com.balaji.sync_engine.conflict.MergeOutcome;
import com.balaji.sync_engine.conflict.MergeStrategyRegistry;
import com.balaji.sync_engine.entity.FieldState;
import com.balaji.sync_engine.repository.FieldStateRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    public record ApplyResult(List<String> conflictReasons) {}

    public ApplyResult applyIncomingChange(String entityType, UUID entityId, String payloadJson,
                                            String incomingHlc, String deviceId) {
        List<String> conflicts = new ArrayList<>();
        JsonNode payload = jsonMapper.readTree(payloadJson);

        payload.properties().forEach(entry -> {
            String fieldName = entry.getKey();
            JsonNode valueNode = entry.getValue();

            if (fieldName.equals("id") || fieldName.equals("createdAt") || fieldName.equals("updatedAt")) {
                return; // not conflict-tracked fields
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
                    incomingValue, incomingHlc);

            MergeOutcome outcome = strategyRegistry.strategyFor(fieldName).merge(input);

            if (outcome.conflict()) {
                conflicts.add(outcome.reason());
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
}
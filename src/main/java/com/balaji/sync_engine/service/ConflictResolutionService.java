package com.balaji.sync_engine.service;

import com.balaji.sync_engine.clock.HLCTimestamp;
import com.balaji.sync_engine.conflict.ConflictType;
import com.balaji.sync_engine.conflict.FieldMergeStrategy;
import com.balaji.sync_engine.conflict.MergeInput;
import com.balaji.sync_engine.conflict.MergeOutcome;
import com.balaji.sync_engine.conflict.MergeStrategyRegistry;
import com.balaji.sync_engine.dto.ConflictInfo;
import com.balaji.sync_engine.entity.ChangeEvent;
import com.balaji.sync_engine.entity.FieldState;
import com.balaji.sync_engine.repository.FieldStateRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

@Service
public class ConflictResolutionService {

    private static final Set<String> UNTRACKED_FIELDS = Set.of("id", "createdAt", "updatedAt", "deletedAt");

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

    public record ApplyResult(List<ConflictInfo> conflicts) {}

    /** @param incomingSeq the log position this event was appended at (already assigned by the caller) */
    public ApplyResult applyIncomingChange(ChangeEvent incoming, long incomingSeq) {
        List<ConflictInfo> conflicts = new ArrayList<>();
        JsonNode payload = jsonMapper.readTree(incoming.getPayload());

        payload.properties().forEach(entry -> {
            String fieldName = entry.getKey();
            if (!isTrackedField(fieldName)) {
                return;
            }
            String incomingValue = toStoredValue(entry.getValue());
            FieldMergeStrategy strategy = strategyRegistry.strategyFor(fieldName);

            Optional<FieldState> existing = fieldStateRepository.findByEntityTypeAndEntityIdAndFieldName(
                    incoming.getEntityType(), incoming.getEntityId(), fieldName);

            if (existing.isEmpty()) {
                if (incomingValue == null) {
                    return; // Do not create field_state rows for null fields on first write
                }
                String initial = strategy.onFirstWrite(fieldName, incomingValue, incoming.getDeviceId());
                saveFieldState(incoming.getEntityType(), incoming.getEntityId(), fieldName, initial,
                        incoming.getHlcTimestamp(), incoming.getDeviceId(), incomingSeq);
                return;
            }

            FieldState current = existing.get();
            MergeInput input = new MergeInput(fieldName, current.getFieldValue(), current.getLastWriteHlc(),
                    current.getLastWriteDeviceId(), incomingValue, incoming.getHlcTimestamp(),
                    incoming.getDeviceId(), observedCurrent(current, incoming));

            MergeOutcome outcome = strategy.merge(input);

            if (outcome.conflict()) {
                conflicts.add(new ConflictInfo(
                        ConflictType.FIELD_CONFLICT, incoming.getEntityType(), incoming.getEntityId(),
                        incoming.getEventId(), fieldName,
                        current.getFieldValue(), current.getLastWriteDeviceId(),
                        incomingValue, incoming.getDeviceId(), outcome.reason()));
                return;
            }

            boolean valueChanged = !Objects.equals(current.getFieldValue(), outcome.resolvedValue());
            boolean hlcAdvances = HLCTimestamp.parse(incoming.getHlcTimestamp())
                    .compareTo(HLCTimestamp.parse(current.getLastWriteHlc())) > 0;

            current.setFieldValue(outcome.resolvedValue());
            // Provenance (clock + device) only moves forward: a losing, older write must never rewind it.
            if (hlcAdvances) {
                current.setLastWriteHlc(incoming.getHlcTimestamp());
                current.setLastWriteDeviceId(incoming.getDeviceId());
            }
            // A later reader must have received every contribution to the value, so the position only moves
            // when the value changed (or the clock advanced); a write that changed nothing leaves it alone.
            if (valueChanged || hlcAdvances) {
                current.setLastWriteSeq(Math.max(
                        current.getLastWriteSeq() == null ? 0L : current.getLastWriteSeq(), incomingSeq));
            }
            fieldStateRepository.save(current);
        });

        return new ApplyResult(conflicts);
    }

    /** Writes field state unconditionally, with no merge strategy. Used for human-made decisions. */
    public void applyAuthoritative(String entityType, UUID entityId, String payloadJson,
                                   String hlc, String deviceId, long seq) {
        JsonNode payload = jsonMapper.readTree(payloadJson);

        payload.properties().forEach(entry -> {
            String fieldName = entry.getKey();
            if (!isTrackedField(fieldName)) {
                return;
            }
            String value = toStoredValue(entry.getValue());

            Optional<FieldState> existing = fieldStateRepository
                    .findByEntityTypeAndEntityIdAndFieldName(entityType, entityId, fieldName);

            if (existing.isEmpty()) {
                saveFieldState(entityType, entityId, fieldName, value, hlc, deviceId, seq);
                return;
            }

            FieldState state = existing.get();
            state.setFieldValue(value);
            state.setLastWriteHlc(hlc);
            state.setLastWriteDeviceId(deviceId);
            state.setLastWriteSeq(seq);
            fieldStateRepository.save(state);
        });
    }

    /** A device always sees its own writes; otherwise it must have received the log up to the current write. */
    private boolean observedCurrent(FieldState current, ChangeEvent incoming) {
        if (incoming.getDeviceId().equals(current.getLastWriteDeviceId())) {
            return true;
        }
        Long basedOn = incoming.getBasedOnSeq();
        Long writeSeq = current.getLastWriteSeq();
        return basedOn != null && writeSeq != null && writeSeq <= basedOn;
    }

    private boolean isTrackedField(String fieldName) {
        return !UNTRACKED_FIELDS.contains(fieldName);
    }

    private String toStoredValue(JsonNode valueNode) {
        if (valueNode.isNull()) {
            return null;
        }
        if (valueNode.isObject() || valueNode.isArray()) {
            return jsonMapper.writeValueAsString(valueNode);   // structured values are stored as JSON text
        }
        return valueNode.asString();
    }

    private void saveFieldState(String entityType, UUID entityId, String fieldName,
                                String value, String hlc, String deviceId, long seq) {
        FieldState state = new FieldState();
        state.setEntityType(entityType);
        state.setEntityId(entityId);
        state.setFieldName(fieldName);
        state.setFieldValue(value);
        state.setLastWriteHlc(hlc);
        state.setLastWriteDeviceId(deviceId);
        state.setLastWriteSeq(seq);
        fieldStateRepository.save(state);
    }
}
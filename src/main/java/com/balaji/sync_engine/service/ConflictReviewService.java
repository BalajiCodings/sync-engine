package com.balaji.sync_engine.service;

import com.balaji.sync_engine.clock.HybridLogicalClock;
import com.balaji.sync_engine.conflict.ConflictStatus;
import com.balaji.sync_engine.conflict.ResolutionAction;
import com.balaji.sync_engine.dto.ConflictInfo;
import com.balaji.sync_engine.dto.ResolveConflictRequest;
import com.balaji.sync_engine.entity.*;
import com.balaji.sync_engine.exception.ConflictAlreadyResolvedException;
import com.balaji.sync_engine.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.*;

@Service
public class ConflictReviewService {

    private static final String SERVER_DEVICE_ID = "server";

    private final ConflictRecordRepository conflictRepository;
    private final FieldStateRepository fieldStateRepository;
    private final EntityDeletionStateRepository deletionStateRepository;
    private final ChangeEventRepository changeEventRepository;
    private final ConflictResolutionService conflictResolutionService;
    private final PatientRecordProjector projector;
    private final HybridLogicalClock clock;
    private final JsonMapper jsonMapper;

    public ConflictReviewService(ConflictRecordRepository conflictRepository,
                                 FieldStateRepository fieldStateRepository,
                                 EntityDeletionStateRepository deletionStateRepository,
                                 ChangeEventRepository changeEventRepository,
                                 ConflictResolutionService conflictResolutionService,
                                 PatientRecordProjector projector,
                                 HybridLogicalClock clock,
                                 JsonMapper jsonMapper) {
        this.conflictRepository = conflictRepository;
        this.fieldStateRepository = fieldStateRepository;
        this.deletionStateRepository = deletionStateRepository;
        this.changeEventRepository = changeEventRepository;
        this.conflictResolutionService = conflictResolutionService;
        this.projector = projector;
        this.clock = clock;
        this.jsonMapper = jsonMapper;
    }

    /** Persists detected conflicts; joins the caller's transaction so case and event commit together. */
    @Transactional
    public List<ConflictInfo> recordDetected(List<ConflictInfo> detected) {
        List<ConflictInfo> recorded = new ArrayList<>(detected.size());
        for (ConflictInfo info : detected) {
            ConflictRecord record = new ConflictRecord();
            record.setConflictType(info.conflictType());
            record.setEntityType(info.entityType());
            record.setEntityId(info.entityId());
            record.setEventId(info.eventId());
            record.setFieldName(info.fieldName());
            record.setCurrentValue(info.currentValue());
            record.setCurrentDeviceId(info.currentDeviceId());
            record.setIncomingValue(info.incomingValue());
            record.setIncomingDeviceId(info.incomingDeviceId());
            record.setMessage(info.message());
            ConflictRecord saved = conflictRepository.save(record);
            recorded.add(info.withConflictId(saved.getId()));
        }
        return recorded;
    }

    @Transactional(readOnly = true)
    public Page<ConflictRecord> list(ConflictStatus status, Pageable pageable) {
        return status == null
                ? conflictRepository.findAll(pageable)
                : conflictRepository.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public ConflictRecord get(UUID id) {
        return load(id);
    }

    @Transactional
    public ConflictRecord resolve(UUID id, ResolveConflictRequest request, String resolvedBy) {
        ConflictRecord conflict = load(id);
        if (conflict.getStatus() == ConflictStatus.RESOLVED) {
            throw new ConflictAlreadyResolvedException(id);
        }
        validate(conflict, request);

        switch (request.action()) {
            case KEEP_CURRENT, ACCEPT_INCOMING, CUSTOM_VALUE -> resolveFieldConflict(conflict, request);
            case CONFIRM_DELETE -> { /* entity is already deleted; nothing to apply */ }
            case RESTORE_ENTITY -> restoreEntity(conflict);
        }

        conflict.setStatus(ConflictStatus.RESOLVED);
        conflict.setResolution(request.action());
        conflict.setResolvedBy(resolvedBy);
        conflict.setResolvedAt(Instant.now());
        return conflictRepository.save(conflict);
    }

    private void resolveFieldConflict(ConflictRecord conflict, ResolveConflictRequest request) {
        String chosen = switch (request.action()) {
            // Use the LIVE value, not the detection-time snapshot.
            case KEEP_CURRENT -> fieldStateRepository
                    .findByEntityTypeAndEntityIdAndFieldName(
                            conflict.getEntityType(), conflict.getEntityId(), conflict.getFieldName())
                    .map(FieldState::getFieldValue)
                    .orElse(conflict.getCurrentValue());
            case ACCEPT_INCOMING -> conflict.getIncomingValue();
            case CUSTOM_VALUE -> request.customValue();
            default -> throw new IllegalStateException("Unexpected action: " + request.action());
        };

        String payload = jsonMapper.writeValueAsString(
                Collections.singletonMap(conflict.getFieldName(), chosen));

        conflict.setResolvedValue(chosen);
        publishDecision(conflict, payload);
        projector.project(conflict.getEntityId());
    }

    private void restoreEntity(ConflictRecord conflict) {
        ChangeEvent rejected = changeEventRepository.findById(conflict.getEventId())
                .orElseThrow(() -> new IllegalStateException(
                        "Rejected event missing for conflict " + conflict.getId()));

        deletionStateRepository.deleteByEntityTypeAndEntityId(conflict.getEntityType(), conflict.getEntityId());
        publishDecision(conflict, rejected.getPayload());
        projector.restore(conflict.getEntityId());
    }

    /** Writes the decision into field_state AND appends it to the event log so devices receive it on pull. */
    private void publishDecision(ConflictRecord conflict, String payloadJson) {
        String hlc = clock.tick().toString();

        conflictResolutionService.applyAuthoritative(
                conflict.getEntityType(), conflict.getEntityId(), payloadJson, hlc, SERVER_DEVICE_ID);

        ChangeEvent event = new ChangeEvent();
        event.setEventId(UUID.randomUUID());
        event.setEntityType(conflict.getEntityType());
        event.setEntityId(conflict.getEntityId());
        event.setChangeType(ChangeType.UPDATE);
        event.setDeviceId(SERVER_DEVICE_ID);
        event.setHlcTimestamp(hlc);
        event.setPayload(payloadJson);
        changeEventRepository.save(event);

        conflict.setResolutionEventId(event.getEventId());
    }

    private void validate(ConflictRecord conflict, ResolveConflictRequest request) {
        ResolutionAction action = request.action();
        if (action == null) {
            throw new IllegalArgumentException("action is required");
        }
        boolean allowed = switch (conflict.getConflictType()) {
            case FIELD_CONFLICT -> action == ResolutionAction.KEEP_CURRENT
                    || action == ResolutionAction.ACCEPT_INCOMING
                    || action == ResolutionAction.CUSTOM_VALUE;
            case DELETE_UPDATE_CONFLICT -> action == ResolutionAction.CONFIRM_DELETE
                    || action == ResolutionAction.RESTORE_ENTITY;
        };
        if (!allowed) {
            throw new IllegalArgumentException(
                    "Action " + action + " is not valid for a " + conflict.getConflictType() + " conflict");
        }
        if (action == ResolutionAction.CUSTOM_VALUE
                && (request.customValue() == null || request.customValue().isBlank())) {
            throw new IllegalArgumentException("customValue is required for CUSTOM_VALUE");
        }
    }

    private ConflictRecord load(UUID id) {
        return conflictRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Conflict not found: " + id));
    }
}
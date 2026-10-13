package com.balaji.sync_engine.service;

import com.balaji.sync_engine.conflict.ConflictType;
import com.balaji.sync_engine.dto.ConflictInfo;
import com.balaji.sync_engine.entity.ChangeEvent;
import com.balaji.sync_engine.entity.ChangeType;
import com.balaji.sync_engine.repository.ChangeEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class SyncEventProcessor {

    private final ChangeEventRepository changeEventRepository;
    private final ConflictResolutionService conflictResolutionService;
    private final DeletionResolutionService deletionResolutionService;
    private final PatientRecordProjector projector;
    private final ConflictReviewService conflictReviewService;
    private final IngestionLock ingestionLock;
    private final ChangeLogNotifier changeLogNotifier;

    public SyncEventProcessor(ChangeEventRepository changeEventRepository,
                              ConflictResolutionService conflictResolutionService,
                              DeletionResolutionService deletionResolutionService,
                              PatientRecordProjector projector,
                              ConflictReviewService conflictReviewService,
                              IngestionLock ingestionLock,
                              ChangeLogNotifier changeLogNotifier) {
        this.changeEventRepository = changeEventRepository;
        this.conflictResolutionService = conflictResolutionService;
        this.deletionResolutionService = deletionResolutionService;
        this.projector = projector;
        this.conflictReviewService = conflictReviewService;
        this.ingestionLock = ingestionLock;
        this.changeLogNotifier = changeLogNotifier;
    }

    public sealed interface EventOutcome permits Skipped, Applied {}
    public record Skipped() implements EventOutcome {}
    public record Applied(List<ConflictInfo> conflicts) implements EventOutcome {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EventOutcome processEvent(ChangeEvent incoming) {
        ingestionLock.acquire();   // first line: arrival order must equal commit order

        validateRequiredFields(incoming);

        if (changeEventRepository.existsById(incoming.getEventId())) {
            return new Skipped();
        }

        validateBasedOnSeq(incoming);

        // Append first, in this same transaction: everything below records this event's log position,
        // and if anything throws, the event rolls back with it.
        long seq = appendToLog(incoming);

        List<ConflictInfo> conflicts = new ArrayList<>();

        if (incoming.getChangeType() == ChangeType.DELETE) {
            deletionResolutionService.recordDeletion(incoming, seq);
            projector.markDeleted(incoming.getEntityId());
        } else {
            DeletionResolutionService.DeletionCheckResult deletion =
                    deletionResolutionService.checkBeforeUpdate(incoming);

            if (deletion.isDeleted()) {
                conflicts.add(new ConflictInfo(
                        ConflictType.DELETE_UPDATE_CONFLICT, incoming.getEntityType(), incoming.getEntityId(),
                        incoming.getEventId(), null, null, null, null,
                        incoming.getDeviceId(), deletion.reason()));
                changeLogNotifier.appended(incoming.getDeviceId());
                return new Applied(conflictReviewService.recordDetected(conflicts));
            }

            conflicts.addAll(conflictResolutionService.applyIncomingChange(incoming, seq).conflicts());

            if (deletion.restored()) {
                projector.restore(incoming.getEntityId());
            } else {
                projector.project(incoming.getEntityId());
            }
        }

        changeLogNotifier.appended(incoming.getDeviceId());
        return new Applied(conflictReviewService.recordDetected(conflicts));
    }

    private void validateRequiredFields(ChangeEvent incoming) {
        if (incoming.getEventId() == null) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (incoming.getEntityType() == null || incoming.getEntityType().isBlank()) {
            throw new IllegalArgumentException("entityType is required");
        }
        if (incoming.getEntityId() == null) {
            throw new IllegalArgumentException("entityId is required");
        }
        if (incoming.getChangeType() == null) {
            throw new IllegalArgumentException("changeType is required");
        }
        if (incoming.getDeviceId() == null || incoming.getDeviceId().isBlank()) {
            throw new IllegalArgumentException("deviceId is required");
        }
        if (incoming.getChangeType() != ChangeType.DELETE
                && (incoming.getPayload() == null || incoming.getPayload().isBlank())) {
            throw new IllegalArgumentException("payload is required for " + incoming.getChangeType());
        }
    }

    /** A device cannot have received positions the log does not have yet. */
    private void validateBasedOnSeq(ChangeEvent incoming) {
        Long basedOn = incoming.getBasedOnSeq();
        if (basedOn == null) {
            return;
        }
        if (basedOn < 0) {
            throw new IllegalArgumentException("basedOnSeq must not be negative");
        }
        long head = changeEventRepository.findHeadSeq();
        if (basedOn > head) {
            throw new IllegalArgumentException(
                    "basedOnSeq " + basedOn + " is ahead of the server log (head is " + head + ")");
        }
    }

    private long appendToLog(ChangeEvent event) {
        changeEventRepository.saveAndFlush(event);
        Long seq = changeEventRepository.findServerSeq(event.getEventId());
        if (seq == null) {
            throw new IllegalStateException("Event " + event.getEventId() + " was saved but has no server_seq");
        }
        return seq;
    }
}
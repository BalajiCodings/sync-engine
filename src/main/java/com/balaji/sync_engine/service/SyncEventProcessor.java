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

    public SyncEventProcessor(ChangeEventRepository changeEventRepository,
                              ConflictResolutionService conflictResolutionService,
                              DeletionResolutionService deletionResolutionService,
                              PatientRecordProjector projector,
                              ConflictReviewService conflictReviewService,
                              IngestionLock ingestionLock) {
        this.changeEventRepository = changeEventRepository;
        this.conflictResolutionService = conflictResolutionService;
        this.deletionResolutionService = deletionResolutionService;
        this.projector = projector;
        this.conflictReviewService = conflictReviewService;
        this.ingestionLock = ingestionLock;
    }

    public sealed interface EventOutcome permits Skipped, Applied {}
    public record Skipped() implements EventOutcome {}
    public record Applied(List<ConflictInfo> conflicts) implements EventOutcome {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EventOutcome processEvent(ChangeEvent incoming) {
        ingestionLock.acquire();   // first line: arrival order must equal commit order

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
                return new Applied(conflictReviewService.recordDetected(conflicts));
            }

            conflicts.addAll(conflictResolutionService.applyIncomingChange(incoming, seq).conflicts());

            if (deletion.restored()) {
                projector.restore(incoming.getEntityId());
            } else {
                projector.project(incoming.getEntityId());
            }
        }

        return new Applied(conflictReviewService.recordDetected(conflicts));
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
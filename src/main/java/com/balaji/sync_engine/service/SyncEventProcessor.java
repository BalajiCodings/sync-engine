package com.balaji.sync_engine.service;

import com.balaji.sync_engine.dto.ConflictInfo;
import com.balaji.sync_engine.conflict.ConflictType;
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

    public SyncEventProcessor(ChangeEventRepository changeEventRepository,
                              ConflictResolutionService conflictResolutionService,
                              DeletionResolutionService deletionResolutionService,
                              PatientRecordProjector projector,
                              ConflictReviewService conflictReviewService) {
        this.changeEventRepository = changeEventRepository;
        this.conflictResolutionService = conflictResolutionService;
        this.deletionResolutionService = deletionResolutionService;
        this.projector = projector;
        this.conflictReviewService = conflictReviewService;
    }

    public sealed interface EventOutcome permits Skipped, Applied {}
    public record Skipped() implements EventOutcome {}
    public record Applied(List<ConflictInfo> conflicts) implements EventOutcome {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EventOutcome processEvent(ChangeEvent incoming) {
        if (changeEventRepository.existsById(incoming.getEventId())) {
            return new Skipped();
        }

        List<ConflictInfo> conflicts = new ArrayList<>();

        if (incoming.getChangeType() == ChangeType.DELETE) {
            deletionResolutionService.recordDeletion(
                    incoming.getEntityType(), incoming.getEntityId(),
                    incoming.getHlcTimestamp(), incoming.getDeviceId());
            projector.markDeleted(incoming.getEntityId());
        } else {
            var deletionCheck = deletionResolutionService.checkBeforeUpdate(
                    incoming.getEntityType(), incoming.getEntityId(),
                    incoming.getHlcTimestamp(), incoming.getDeviceId());

            if (deletionCheck.isDeleted()) {
                conflicts.add(new ConflictInfo(
                        ConflictType.DELETE_UPDATE_CONFLICT, incoming.getEntityType(), incoming.getEntityId(),
                        incoming.getEventId(), null, null, null, null,
                        incoming.getDeviceId(), deletionCheck.reason()));
                changeEventRepository.save(incoming);
                return new Applied(conflictReviewService.recordDetected(conflicts));   // <-- changed
            }

            ConflictResolutionService.ApplyResult result = conflictResolutionService.applyIncomingChange(
                    incoming.getEventId(), incoming.getEntityType(), incoming.getEntityId(),
                    incoming.getPayload(), incoming.getHlcTimestamp(), incoming.getDeviceId());
            conflicts.addAll(result.conflicts());
            projector.project(incoming.getEntityId());
        }

        changeEventRepository.save(incoming);
        return new Applied(conflictReviewService.recordDetected(conflicts));           // <-- changed
    }
}
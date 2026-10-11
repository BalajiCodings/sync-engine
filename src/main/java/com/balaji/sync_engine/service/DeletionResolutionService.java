package com.balaji.sync_engine.service;

import com.balaji.sync_engine.clock.HLCTimestamp;
import com.balaji.sync_engine.entity.ChangeEvent;
import com.balaji.sync_engine.entity.EntityDeletionState;
import com.balaji.sync_engine.repository.EntityDeletionStateRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class DeletionResolutionService {

    private final EntityDeletionStateRepository deletionRepository;

    public DeletionResolutionService(EntityDeletionStateRepository deletionRepository) {
        this.deletionRepository = deletionRepository;
    }

    public record DeletionCheckResult(boolean isDeleted, boolean restored, String reason) {
        static DeletionCheckResult notDeleted() {
            return new DeletionCheckResult(false, false, null);
        }
    }

    /**
     * Call before applying a non-delete change. An update that causally follows the deletion
     * (same device with a later clock, or a device that had received the deletion) is a deliberate
     * restore. Anything else is a delete-vs-update conflict.
     */
    public DeletionCheckResult checkBeforeUpdate(ChangeEvent update) {
        Optional<EntityDeletionState> deletion = deletionRepository
                .findByEntityTypeAndEntityId(update.getEntityType(), update.getEntityId());
        if (deletion.isEmpty()) {
            return DeletionCheckResult.notDeleted();
        }

        EntityDeletionState state = deletion.get();
        boolean sameDevice = state.getDeletedByDeviceId().equals(update.getDeviceId());

        boolean followsDeletion;
        if (sameDevice) {
            followsDeletion = HLCTimestamp.parse(update.getHlcTimestamp())
                    .compareTo(HLCTimestamp.parse(state.getDeletionHlc())) > 0;
        } else {
            followsDeletion = state.getDeletionSeq() != null && update.getBasedOnSeq() != null
                    && state.getDeletionSeq() <= update.getBasedOnSeq();
        }

        if (followsDeletion) {
            deletionRepository.deleteByEntityTypeAndEntityId(update.getEntityType(), update.getEntityId());
            return new DeletionCheckResult(false, true, null);
        }

        return new DeletionCheckResult(true, false,
                "Entity was deleted by device '" + state.getDeletedByDeviceId() + "' but device '"
                + update.getDeviceId() + "' updated it without having received the deletion "
                + "(DELETE_UPDATE_CONFLICT) — requires manual review");
    }

    /** Idempotent: if the entity is already deleted, the first deletion stays the record of truth. */
    public void recordDeletion(ChangeEvent deletion, long deletionSeq) {
        if (deletionRepository.findByEntityTypeAndEntityId(
                deletion.getEntityType(), deletion.getEntityId()).isPresent()) {
            return;
        }
        EntityDeletionState state = new EntityDeletionState();
        state.setEntityType(deletion.getEntityType());
        state.setEntityId(deletion.getEntityId());
        state.setDeletionHlc(deletion.getHlcTimestamp());
        state.setDeletedByDeviceId(deletion.getDeviceId());
        state.setDeletionSeq(deletionSeq);
        deletionRepository.save(state);
    }
}
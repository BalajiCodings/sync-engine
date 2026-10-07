package com.balaji.sync_engine.service;

import com.balaji.sync_engine.clock.HLCTimestamp;
import com.balaji.sync_engine.entity.EntityDeletionState;
import com.balaji.sync_engine.repository.EntityDeletionStateRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class DeletionResolutionService {

    private final EntityDeletionStateRepository deletionRepository;

    public DeletionResolutionService(EntityDeletionStateRepository deletionRepository) {
        this.deletionRepository = deletionRepository;
    }

    public record DeletionCheckResult(boolean isDeleted, String reason) {}

    /**
     * Call before applying a non-delete change. Determines whether the entity
     * is currently deleted, and if the update should proceed anyway (update
     * is causally/device-sequentially later than the delete) or be rejected.
     */
    public DeletionCheckResult checkBeforeUpdate(String entityType, UUID entityId,
                                                  String updateHlc, String updateDeviceId) {
        Optional<EntityDeletionState> deletion =
                deletionRepository.findByEntityTypeAndEntityId(entityType, entityId);

        if (deletion.isEmpty()) {
            return new DeletionCheckResult(false, null);
        }

        EntityDeletionState state = deletion.get();
        boolean sameDevice = state.getDeletedByDeviceId().equals(updateDeviceId);

        if (sameDevice) {
            HLCTimestamp deletionTs = HLCTimestamp.parse(state.getDeletionHlc());
            HLCTimestamp updateTs = HLCTimestamp.parse(updateHlc);
            if (updateTs.compareTo(deletionTs) > 0) {
                // Same device un-deleting its own prior delete via a later edit --
                // treat as an explicit restore.
                deletionRepository.deleteByEntityTypeAndEntityId(entityType, entityId);
                return new DeletionCheckResult(false, null);
            }
        }

        // Different device editing a deleted entity -- this is a real conflict.
        // Default policy: the update is rejected, but flagged for human review
        // rather than silently discarded or silently resurrecting the record.
        return new DeletionCheckResult(true,
                "Entity was deleted by device '" + state.getDeletedByDeviceId()
                + "' but device '" + updateDeviceId + "' attempted a concurrent update "
                + "(Scenario: DELETE_UPDATE_CONFLICT) — requires manual review");
    }

    public void recordDeletion(String entityType, UUID entityId, String deletionHlc, String deviceId) {
        EntityDeletionState state = new EntityDeletionState();
        state.setEntityType(entityType);
        state.setEntityId(entityId);
        state.setDeletionHlc(deletionHlc);
        state.setDeletedByDeviceId(deviceId);
        deletionRepository.save(state);
    }
}
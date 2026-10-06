package com.balaji.sync_engine.service;

import com.balaji.sync_engine.clock.HLCTimestamp;

import com.balaji.sync_engine.clock.HybridLogicalClock;
import com.balaji.sync_engine.dto.SyncPullRequest;
import com.balaji.sync_engine.dto.SyncPullResponse;
import com.balaji.sync_engine.dto.SyncPushRequest;
import com.balaji.sync_engine.dto.SyncPushResponse;
import com.balaji.sync_engine.entity.ChangeEvent;
import com.balaji.sync_engine.entity.ChangeType;
import com.balaji.sync_engine.repository.ChangeEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class SyncService {

    private final ChangeEventRepository changeEventRepository;
    private final HybridLogicalClock clock;
    private final ConflictResolutionService conflictResolutionService;

    public SyncService(ChangeEventRepository changeEventRepository, HybridLogicalClock clock, ConflictResolutionService conflictResolutionService) {
        this.changeEventRepository = changeEventRepository;
        this.clock = clock;
        this.conflictResolutionService = conflictResolutionService;
    }

    public SyncPullResponse pull(SyncPullRequest request) {
        List<ChangeEvent> changes;

        if (request.lastSyncedHlc() == null || request.lastSyncedHlc().isBlank()) {
            // First-time sync for this device — for now, return everything.
            // Phase 9 note: at real scale this should be a snapshot, not full history.
            changes = changeEventRepository.findAllByOrderByServerReceivedAtAsc();
        } else {
            changes = changeEventRepository
                    .findByHlcTimestampGreaterThanOrderByHlcTimestampAsc(request.lastSyncedHlc());
        }

        HLCTimestamp checkpoint = clock.tick();
        return new SyncPullResponse(changes, checkpoint.toString());
    }

    @Transactional
    public SyncPushResponse push(SyncPushRequest request) {
        List<String> allConflictReasons = new ArrayList<>();
        int accepted = 0;

        for (ChangeEvent incoming : request.changes()) {
            HLCTimestamp incomingTs = HLCTimestamp.parse(incoming.getHlcTimestamp());
            clock.update(incomingTs);

            boolean alreadyExists = changeEventRepository.existsById(incoming.getEventId());
            if (alreadyExists) {
                continue;
            }

            if (incoming.getChangeType() != ChangeType.DELETE) {
                ConflictResolutionService.ApplyResult result = conflictResolutionService.applyIncomingChange(
                        incoming.getEntityType(), incoming.getEntityId(), incoming.getPayload(),
                        incoming.getHlcTimestamp(), incoming.getDeviceId());
                allConflictReasons.addAll(result.conflictReasons());
            }

            changeEventRepository.save(incoming);
            accepted++;
        }

        HLCTimestamp checkpoint = clock.tick();
        return new SyncPushResponse(accepted, allConflictReasons, checkpoint.toString());
    }
}
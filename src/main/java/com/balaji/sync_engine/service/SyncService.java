package com.balaji.sync_engine.service;

import com.balaji.sync_engine.clock.HLCTimestamp;
import com.balaji.sync_engine.clock.HybridLogicalClock;
import com.balaji.sync_engine.dto.SyncPullRequest;
import com.balaji.sync_engine.dto.SyncPullResponse;
import com.balaji.sync_engine.dto.SyncPushRequest;
import com.balaji.sync_engine.dto.SyncPushResponse;
import com.balaji.sync_engine.entity.ChangeEvent;
import com.balaji.sync_engine.repository.ChangeEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class SyncService {

    private final ChangeEventRepository changeEventRepository;
    private final HybridLogicalClock clock;

    public SyncService(ChangeEventRepository changeEventRepository, HybridLogicalClock clock) {
        this.changeEventRepository = changeEventRepository;
        this.clock = clock;
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
        List<String> conflicted = new ArrayList<>();
        int accepted = 0;

        for (ChangeEvent incoming : request.changes()) {
            HLCTimestamp incomingTs = HLCTimestamp.parse(incoming.getHlcTimestamp());
            clock.update(incomingTs);

            boolean alreadyExists = changeEventRepository.existsById(incoming.getEventId());
            if (alreadyExists) {
                // Idempotency: this event was already applied in a previous,
                // possibly interrupted, push. Skip silently — covered properly in Phase 6.
                continue;
            }

            changeEventRepository.save(incoming);
            accepted++;
        }

        HLCTimestamp checkpoint = clock.tick();
        return new SyncPushResponse(accepted, conflicted, checkpoint.toString());
    }
}
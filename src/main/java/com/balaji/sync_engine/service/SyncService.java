package com.balaji.sync_engine.service;

import com.balaji.sync_engine.clock.HLCTimestamp;
import com.balaji.sync_engine.clock.HybridLogicalClock;
import com.balaji.sync_engine.dto.*;
import com.balaji.sync_engine.entity.ChangeEvent;
import com.balaji.sync_engine.repository.ChangeEventRepository;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SyncService {

    private static final int MAX_BATCH_SIZE = 200;

    private final ChangeEventRepository changeEventRepository;
    private final HybridLogicalClock clock;
    private final SyncEventProcessor eventProcessor;

    public SyncService(ChangeEventRepository changeEventRepository,
                        HybridLogicalClock clock,
                        SyncEventProcessor eventProcessor) {
        this.changeEventRepository = changeEventRepository;
        this.clock = clock;
        this.eventProcessor = eventProcessor;
    }

    private static final int DEFAULT_PULL_LIMIT = 500;
    private static final int MAX_PULL_LIMIT = 1000;

    @Operation(summary = "irrelevant here, controller-level only") // (just a reminder -- annotation stays on controller, not service)
    public SyncPullResponse pull(SyncPullRequest request) {
        int limit = resolveLimit(request.limit());
        // Fetch one extra beyond the limit, purely to detect whether more data
        // remains, without a separate COUNT query.
        Pageable pageable = PageRequest.of(0, limit + 1);

        List<ChangeEvent> changes;
        if (request.lastSyncedHlc() == null || request.lastSyncedHlc().isBlank()) {
            changes = changeEventRepository.findAllByOrderByServerReceivedAtAsc(pageable);
        } else {
            changes = changeEventRepository
                    .findByHlcTimestampGreaterThanOrderByHlcTimestampAsc(request.lastSyncedHlc(), pageable);
        }

        boolean hasMore = changes.size() > limit;
        if (hasMore) {
            changes = changes.subList(0, limit);
        }

        String checkpoint = hasMore
                ? changes.get(changes.size() - 1).getHlcTimestamp()
                : clock.tick().toString();

        return new SyncPullResponse(changes, checkpoint, hasMore);
    }

	    private int resolveLimit(Integer requested) {
	        if (requested == null) {
	            return DEFAULT_PULL_LIMIT;
	        }
	        return Math.min(Math.max(requested, 1), MAX_PULL_LIMIT);
	    }

    public SyncPushResponse push(SyncPushRequest request) {
        if (request.changes().size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "Batch too large: received " + request.changes().size()
                    + " events, maximum is " + MAX_BATCH_SIZE
                    + ". Split into smaller chunks and push sequentially.");
        }

        List<ConflictInfo> allConflicts = new ArrayList<>();
        List<RejectedEvent> rejected = new ArrayList<>();
        int accepted = 0;

        for (ChangeEvent incoming : request.changes()) {
            HLCTimestamp incomingTs = HLCTimestamp.parse(incoming.getHlcTimestamp());
            clock.update(incomingTs);

            try {
                SyncEventProcessor.EventOutcome outcome = eventProcessor.processEvent(incoming);

                switch (outcome) {
                    case SyncEventProcessor.Skipped ignored -> {
                        // already applied in a prior push — idempotent no-op
                    }
                    case SyncEventProcessor.Applied applied -> {
                        allConflicts.addAll(applied.conflicts());
                        accepted++;
                    }
                }
            } catch (Exception ex) {
                rejected.add(new RejectedEvent(incoming.getEventId(), ex.getMessage()));
            }
        }

        HLCTimestamp checkpoint = clock.tick();
        return new SyncPushResponse(accepted, allConflicts, rejected, checkpoint.toString());
    }
}
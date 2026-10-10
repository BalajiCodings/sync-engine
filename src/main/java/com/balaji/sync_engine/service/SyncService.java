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

    public SyncPullResponse pull(SyncPullRequest request) {
        int limit = resolveLimit(request.limit());
        long cursor = request.cursor() == null ? 0L : request.cursor();
        if (cursor < 0) {
            throw new IllegalArgumentException("cursor must not be negative");
        }

        // Fetch one extra row only to learn whether more remain, without a COUNT query.
        List<ChangeEvent> batch = changeEventRepository
                .findByServerSeqGreaterThanOrderByServerSeqAsc(cursor, PageRequest.of(0, limit + 1));

        boolean hasMore = batch.size() > limit;
        List<ChangeEvent> changes = hasMore ? new ArrayList<>(batch.subList(0, limit)) : batch;

        long nextCursor = changes.isEmpty() ? cursor : changes.get(changes.size() - 1).getServerSeq();
        return new SyncPullResponse(changes, nextCursor, hasMore, clock.tick().toString());
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
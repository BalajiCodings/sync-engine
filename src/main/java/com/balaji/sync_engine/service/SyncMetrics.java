package com.balaji.sync_engine.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central Micrometer metrics facade for the Sync Engine.
 * Encapsulates counters, timers, and gauges to prevent metric naming drift.
 */
@Component
public class SyncMetrics {

    private final MeterRegistry registry;

    // Counters
    private final Counter pushBatchesSuccess;
    private final Counter pushBatchesFailure;
    private final Counter eventsAccepted;
    private final Counter eventsDeduplicated;
    private final Counter fieldConflicts;
    private final Counter deleteUpdateConflicts;

    // Timers
    private final Timer lockWaitTimer;
    private final Timer pushBatchTimer;

    // Live Gauges
    private final AtomicInteger activeSseConnections = new AtomicInteger(0);

    public SyncMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.pushBatchesSuccess = Counter.builder("sync.push.batches")
                .tag("status", "success")
                .description("Total successfully processed push batches")
                .register(registry);

        this.pushBatchesFailure = Counter.builder("sync.push.batches")
                .tag("status", "failure")
                .description("Total failed push batches")
                .register(registry);

        this.eventsAccepted = Counter.builder("sync.events.accepted")
                .description("Total change events accepted and appended to the server log")
                .register(registry);

        this.eventsDeduplicated = Counter.builder("sync.events.deduplicated")
                .description("Total change events identified as duplicate and skipped (idempotent no-op)")
                .register(registry);

        this.fieldConflicts = Counter.builder("sync.conflicts.detected")
                .tag("type", "FIELD_CONFLICT")
                .description("Total concurrent field-level conflicts flagged for review")
                .register(registry);

        this.deleteUpdateConflicts = Counter.builder("sync.conflicts.detected")
                .tag("type", "DELETE_UPDATE_CONFLICT")
                .description("Total delete-update collision conflicts flagged for review")
                .register(registry);

        this.lockWaitTimer = Timer.builder("sync.lock.wait.duration")
                .description("Time spent waiting to acquire the PostgreSQL transactional advisory lock")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.pushBatchTimer = Timer.builder("sync.push.batch.duration")
                .description("End-to-end execution time for processing a push batch")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        registry.gauge("sync.sse.active.connections", activeSseConnections);
    }

    public void recordPushBatchSuccess(int acceptedCount, int deduplicatedCount) {
        pushBatchesSuccess.increment();
        if (acceptedCount > 0) {
            eventsAccepted.increment(acceptedCount);
        }
        if (deduplicatedCount > 0) {
            eventsDeduplicated.increment(deduplicatedCount);
        }
    }

    public void recordPushBatchFailure() {
        pushBatchesFailure.increment();
    }

    public void recordConflict(com.balaji.sync_engine.conflict.ConflictType type) {
        if (type == com.balaji.sync_engine.conflict.ConflictType.DELETE_UPDATE_CONFLICT) {
            deleteUpdateConflicts.increment();
        } else {
            fieldConflicts.increment();
        }
    }

    /**
     * Executes the given runnable while timing how long it takes to acquire the advisory lock.
     */
    public void recordLockWait(Runnable lockAcquisition) {
        lockWaitTimer.record(lockAcquisition);
    }

    /**
     * Times the execution of an entire push batch operation.
     */
    public <T> T recordPushDuration(Callable<T> callable) throws Exception {
        return pushBatchTimer.recordCallable(callable);
    }

    public void sseConnectionOpened() {
        activeSseConnections.incrementAndGet();
    }

    public void sseConnectionClosed() {
        activeSseConnections.updateAndGet(val -> Math.max(0, val - 1));
    }

    public int getActiveSseCount() {
        return activeSseConnections.get();
    }
}
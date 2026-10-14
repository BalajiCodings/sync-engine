package com.balaji.sync_engine.service;

import com.balaji.sync_engine.conflict.ConflictType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncMetricsTest {

    private SimpleMeterRegistry registry;
    private SyncMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new SyncMetrics(registry);
    }

    @Test
    void recordsPushBatchSuccessAndEventCounts() {
        metrics.recordPushBatchSuccess(5, 2);

        assertEquals(1.0, registry.get("sync.push.batches").tag("status", "success").counter().count());
        assertEquals(5.0, registry.get("sync.events.accepted").counter().count());
        assertEquals(2.0, registry.get("sync.events.deduplicated").counter().count());
    }

    @Test
    void recordsConflictsByType() {
        metrics.recordConflict(ConflictType.FIELD_CONFLICT);
        metrics.recordConflict(ConflictType.FIELD_CONFLICT);
        metrics.recordConflict(ConflictType.DELETE_UPDATE_CONFLICT);

        assertEquals(2.0, registry.get("sync.conflicts.detected").tag("type", "FIELD_CONFLICT").counter().count());
        assertEquals(1.0, registry.get("sync.conflicts.detected").tag("type", "DELETE_UPDATE_CONFLICT").counter().count());
    }

    @Test
    void managesActiveSseGaugeCorrectly() {
        assertEquals(0, metrics.getActiveSseCount());

        metrics.sseConnectionOpened();
        metrics.sseConnectionOpened();
        assertEquals(2, metrics.getActiveSseCount());

        metrics.sseConnectionClosed();
        assertEquals(1, metrics.getActiveSseCount());

        metrics.sseConnectionClosed();
        metrics.sseConnectionClosed(); // Guard against going below 0
        assertEquals(0, metrics.getActiveSseCount());
    }

    @Test
    void recordsLockWaitDuration() {
        metrics.recordLockWait(() -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {}
        });

        assertEquals(1L, registry.get("sync.lock.wait.duration").timer().count());
    }
}
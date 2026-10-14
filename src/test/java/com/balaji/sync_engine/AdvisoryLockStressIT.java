package com.balaji.sync_engine;

import static org.junit.jupiter.api.Assertions.*;

import com.balaji.sync_engine.clock.HybridLogicalClock;
import com.balaji.sync_engine.dto.SyncPushRequest;
import com.balaji.sync_engine.dto.SyncPushResponse;
import com.balaji.sync_engine.entity.ChangeEvent;
import com.balaji.sync_engine.entity.ChangeType;
import com.balaji.sync_engine.repository.ChangeEventRepository;
import com.balaji.sync_engine.repository.PatientRecordRepository;
import com.balaji.sync_engine.service.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrency Stress Test: Proves that PostgreSQL transactional advisory locks
 * serialize high multi-threaded push contention with 0 deadlocks and 0 gaps.
 */
class AdvisoryLockStressIT extends BaseIntegrationTest {

    private static final int CONCURRENT_THREADS = 15;
    private static final int EVENTS_PER_THREAD = 10;

    @Autowired private SyncService syncService;
    @Autowired private ChangeEventRepository changeEventRepository;
    @Autowired private PatientRecordRepository patientRecordRepository;
    @Autowired private HybridLogicalClock clock;

    @BeforeEach
    void clean() {
        patientRecordRepository.deleteAll();
        changeEventRepository.deleteAll();
    }

    @Test
    void highConcurrencyPush_guaranteesContiguousGaplessSequences_andNoDeadlocks() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch readyGate = new CountDownLatch(CONCURRENT_THREADS);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(CONCURRENT_THREADS);

        AtomicInteger successfulBatches = new AtomicInteger(0);
        List<Throwable> exceptions = new CopyOnWriteArrayList<>();

        // Spin up 15 parallel workers
        for (int threadId = 0; threadId < CONCURRENT_THREADS; threadId++) {
            final String deviceId = "worker-device-" + threadId;

            executor.submit(() -> {
                readyGate.countDown();
                try {
                    startGun.await(); // Wait for all threads to line up so they hit the server together

                    List<ChangeEvent> batch = new ArrayList<>();
                    for (int i = 0; i < EVENTS_PER_THREAD; i++) {
                        ChangeEvent event = new ChangeEvent();
                        event.setEventId(UUID.randomUUID());
                        event.setEntityType("PatientRecord");
                        event.setEntityId(UUID.randomUUID());
                        event.setChangeType(ChangeType.CREATE);
                        event.setDeviceId(deviceId);
                        event.setHlcTimestamp(clock.tick().toString());
                        event.setPayload("{\"patientName\":\"StressPatient\",\"weight\":65.0}");
                        batch.add(event);
                    }

                    SyncPushResponse response = syncService.push(new SyncPushRequest(deviceId, batch));
                    if (response.acceptedCount() == EVENTS_PER_THREAD && response.rejectedEvents().isEmpty()) {
                        successfulBatches.incrementAndGet();
                    }
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    finishGate.countDown();
                }
            });
        }

        // Wait for all threads to initialize, then fire the starting gun
        readyGate.await();
        long startTime = System.currentTimeMillis();
        startGun.countDown();

        boolean finishedInTime = finishGate.await(60, TimeUnit.SECONDS);
        long elapsedMs = System.currentTimeMillis() - startTime;
        executor.shutdown();

        // --- VERIFICATIONS ---
        assertTrue(finishedInTime, "All concurrent pushes must complete within 60s");
        assertTrue(exceptions.isEmpty(), "Zero exceptions allowed (got: " + exceptions + ")");
        assertEquals(CONCURRENT_THREADS, successfulBatches.get(), "All concurrent batches must succeed");

        int expectedTotalEvents = CONCURRENT_THREADS * EVENTS_PER_THREAD;
        long actualEventCount = changeEventRepository.count();
        assertEquals(expectedTotalEvents, actualEventCount, "Every single event must be committed");

        // GAPLESS SEQUENCE AUDIT:
        // Query all server_seq in ascending order and assert server_seq[i] == i + 1
        List<ChangeEvent> allCommitted = changeEventRepository.findByServerSeqGreaterThanOrderByServerSeqAsc(
                0L, org.springframework.data.domain.PageRequest.of(0, expectedTotalEvents + 10));

        assertEquals(expectedTotalEvents, allCommitted.size());
        for (int i = 0; i < allCommitted.size(); i++) {
            long expectedSeq = i + 1;
            long actualSeq = allCommitted.get(i).getServerSeq();
            assertEquals(expectedSeq, actualSeq, "Sequence gap detected at index " + i + "! Ingestion must be strictly gapless.");
        }

        System.out.printf("✅ [STRESS TEST PASSED] Processed %d events across %d parallel threads in %d ms (Zero deadlocks, Gapless: 1..%d)%n",
                expectedTotalEvents, CONCURRENT_THREADS, elapsedMs, expectedTotalEvents);
    }
}
package com.balaji.sync_engine.clock;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HybridLogicalClockTest {

    @Test
    void tick_shouldProduceIncreasingTimestamps() {
        HybridLogicalClock clock = new HybridLogicalClock("deviceA");

        HLCTimestamp first = clock.tick();
        HLCTimestamp second = clock.tick();

        assertTrue(second.compareTo(first) > 0,
                "Second tick should always be ordered after the first tick");
    }

    @Test
    void update_shouldAdvancePastReceivedTimestamp() {
        HybridLogicalClock localClock = new HybridLogicalClock("deviceA");
        HLCTimestamp remoteTimestamp = new HLCTimestamp(System.currentTimeMillis() + 10_000, 5, "deviceB");

        HLCTimestamp result = localClock.update(remoteTimestamp);

        assertTrue(result.compareTo(remoteTimestamp) > 0,
                "Local clock must produce a timestamp strictly after the received one");
    }

    @Test
    void concurrentTicks_shouldNeverProduceDuplicateTimestamps() throws InterruptedException {
        HybridLogicalClock clock = new HybridLogicalClock("deviceA");
        int threadCount = 50;
        java.util.Set<HLCTimestamp> seen = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                HLCTimestamp ts = clock.tick();
                assertTrue(seen.add(ts), "Duplicate timestamp detected: " + ts);
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        assertEquals(threadCount, seen.size(), "All timestamps from concurrent ticks must be unique");
    }
}
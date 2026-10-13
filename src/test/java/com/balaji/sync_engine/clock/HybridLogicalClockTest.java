
package com.balaji.sync_engine.clock;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HybridLogicalClockTest {

    private HybridLogicalClock clock;

    @BeforeEach
    void setUp() {
        clock = new HybridLogicalClock("node");
    }

    @Test
    void tickReturnsTimestampForCurrentWallClock() {
        long before = System.currentTimeMillis();

        HLCTimestamp t = clock.tick();

        long after = System.currentTimeMillis();

        assertTrue(t.physicalTime() >= before);
        assertTrue(t.physicalTime() <= after);
        assertEquals("node", t.nodeId());
        assertEquals(0, t.counter());
    }

    @Test
    void ticksInTheSameMillisecondBumpTheCounter() {
        // The first tick initializes the clock.
        HLCTimestamp first = clock.tick();

        // Subsequent ticks cannot move the physical time backwards.
        HLCTimestamp second = clock.tick();
        HLCTimestamp third = clock.tick();

        assertTrue(second.compareTo(first) > 0);
        assertTrue(third.compareTo(second) > 0);
    }

    @Test
    void updateWithFutureTimestampJumpsAheadOfIt() {
        long futureTime = System.currentTimeMillis() + 60_000;

        HLCTimestamp remote =
            new HLCTimestamp(futureTime, 3, "other");

        HLCTimestamp t = clock.update(remote);

        assertEquals(futureTime, t.physicalTime());
        assertEquals(4, t.counter());
        assertEquals("node", t.nodeId());
        assertTrue(t.compareTo(remote) > 0);
    }

    @Test
    void updateWithPastTimestampStillAdvances() {
        HLCTimestamp before = clock.tick();

        HLCTimestamp t = clock.update(
            new HLCTimestamp(1, 0, "other")
        );

        assertTrue(t.compareTo(before) > 0);
    }

    @Test
    void updateWithEqualPhysicalTimeTakesLargerCounterPlusOne() {
        long futureTime = System.currentTimeMillis() + 60_000;

        // Initialize the local clock at a lower physical time.
        clock.tick();

        // Advance the local clock to a known future physical time.
        clock.update(new HLCTimestamp(futureTime, 5, "other"));

        HLCTimestamp t = clock.update(
            new HLCTimestamp(futureTime, 9, "remote")
        );

        assertEquals(futureTime, t.physicalTime());
        assertEquals(10, t.counter());
    }

    @Test
    void updateWhenWallClockIsAheadOfEverythingResetsCounter() {
        long futureTime = System.currentTimeMillis() + 60_000;

        HLCTimestamp t = clock.update(
            new HLCTimestamp(futureTime, 5, "other")
        );

        assertEquals(futureTime, t.physicalTime());
        assertEquals(6, t.counter());
    }

    @Test
    void timestampsIncreaseUnderMixedUse() {
        HLCTimestamp previous = clock.tick();

        for (int i = 0; i < 1_000; i++) {
            HLCTimestamp next = (i % 5 == 0)
                ? clock.update(
                    new HLCTimestamp(
                        System.currentTimeMillis() + (i % 3),
                        i % 4,
                        "other"
                    )
                )
                : clock.tick();

            assertTrue(
                next.compareTo(previous) > 0,
                "Timestamp did not increase at iteration " + i
            );

            previous = next;
        }
    }

    @Test
    void productionConstructorUsesTheRealClock() {
        HybridLogicalClock real = new HybridLogicalClock("node");

        long before = System.currentTimeMillis();
        long got = real.tick().physicalTime();

        assertTrue(got >= before);
    }
}

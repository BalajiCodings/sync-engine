package com.balaji.sync_engine.conflict;

import com.balaji.sync_engine.clock.HLCTimestamp;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ManualReviewStrategyTest {

    private final ManualReviewStrategy strategy = new ManualReviewStrategy();

    private static String hlc(long time, String node) {
        return new HLCTimestamp(time, 0, node).toString();
    }

    private static MergeInput input(String currentDevice, long currentTime,
                                    String incomingDevice, long incomingTime, boolean observed) {
        return new MergeInput("dosage", "250mg", hlc(currentTime, currentDevice), currentDevice,
                "500mg", hlc(incomingTime, incomingDevice), incomingDevice, observed);
    }

    @Test
    void sameDeviceNewerEditIsSequential() {
        MergeOutcome outcome = strategy.merge(input("deviceA", 1000, "deviceA", 2000, true));
        assertFalse(outcome.conflict());
        assertEquals("500mg", outcome.resolvedValue());
    }

    @Test
    void sameDeviceOlderEditIsIgnoredNotFlagged() {
        MergeOutcome outcome = strategy.merge(input("deviceA", 2000, "deviceA", 1000, true));
        assertFalse(outcome.conflict());
        assertEquals("250mg", outcome.resolvedValue());
    }

    @Test
    void differentDeviceThatHadReceivedTheCurrentValueIsSequential() {
        MergeOutcome outcome = strategy.merge(input("deviceA", 1000, "deviceB", 2000, true));
        assertFalse(outcome.conflict());
        assertEquals("500mg", outcome.resolvedValue());
    }

    @Test
    void differentDeviceThatHadNotReceivedTheCurrentValueIsAConflict() {
        MergeOutcome outcome = strategy.merge(input("deviceA", 1000, "deviceB", 2000, false));
        assertTrue(outcome.conflict());
        assertNull(outcome.resolvedValue());
    }

    @Test
    void legacyInputWithoutCausalityKeepsTheOldBehaviour() {
        MergeInput legacy = new MergeInput("dosage", "250mg", hlc(1000, "deviceA"), "deviceA",
                "500mg", hlc(2000, "deviceB"), "deviceB");
        assertTrue(strategy.merge(legacy).conflict());
    }
}
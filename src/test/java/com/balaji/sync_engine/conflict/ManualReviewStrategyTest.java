package com.balaji.sync_engine.conflict;

import static org.junit.jupiter.api.Assertions.*;

import com.balaji.sync_engine.clock.HLCTimestamp;
import org.junit.jupiter.api.Test;

class ManualReviewStrategyTest {

    private final ManualReviewStrategy strategy = new ManualReviewStrategy();

    private static String hlc(long t) {
        return new HLCTimestamp(t, 0, "n").toString();
    }

    @Test
    void sameDeviceLaterUpdateIsAccepted() {
        MergeOutcome out = strategy.merge(new MergeInput("dosage", "500", hlc(1), "dev-a", "750", hlc(2), "dev-a"));
        assertFalse(out.conflict());
        assertEquals("750", out.resolvedValue());
    }

    @Test
    void sameDeviceOutOfOrderUpdateIsIgnored() {
        MergeOutcome out = strategy.merge(new MergeInput("dosage", "750", hlc(2), "dev-a", "500", hlc(1), "dev-a"));
        assertFalse(out.conflict());
        assertEquals("750", out.resolvedValue());
    }

    @Test
    void differentDevicesWithoutAwarenessIsAConflict() {
        MergeOutcome out = strategy.merge(new MergeInput("dosage", "500", hlc(1), "dev-a", "750", hlc(2), "dev-b"));
        assertTrue(out.conflict());
        assertNull(out.resolvedValue());
        assertTrue(out.reason().contains("dev-a") && out.reason().contains("dev-b"));
        assertTrue(out.reason().contains("500") && out.reason().contains("750"));
    }

    @Test
    void differentDevicesButIncomingSawTheCurrentValueIsSequential() {
        MergeOutcome out = strategy.merge(
                new MergeInput("dosage", "500", hlc(1), "dev-a", "750", hlc(2), "dev-b", true));
        assertFalse(out.conflict());
        assertEquals("750", out.resolvedValue());
    }

    @Test
    void conflictIsDecidedByCausalityNotByWhoseClockIsLater() {
        // incoming has the *earlier* timestamp but never saw current -> still a conflict
        MergeOutcome out = strategy.merge(new MergeInput("dosage", "500", hlc(9), "dev-a", "750", hlc(1), "dev-b"));
        assertTrue(out.conflict());
    }

    @Test
    void nullCurrentDeviceIsNeverTreatedAsSameDevice() {
        MergeOutcome out = strategy.merge(new MergeInput("dosage", "500", hlc(1), null, "750", hlc(2), null));
        assertTrue(out.conflict());
    }
}

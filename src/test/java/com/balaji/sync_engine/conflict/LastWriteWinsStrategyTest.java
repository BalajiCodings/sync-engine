package com.balaji.sync_engine.conflict;

import static org.junit.jupiter.api.Assertions.*;

import com.balaji.sync_engine.clock.HLCTimestamp;
import org.junit.jupiter.api.Test;

class LastWriteWinsStrategyTest {

    private final LastWriteWinsStrategy strategy = new LastWriteWinsStrategy();

    private static String hlc(long t, int c, String n) {
        return new HLCTimestamp(t, c, n).toString();
    }

    private static MergeInput input(String curHlc, String inHlc) {
        return new MergeInput("weight", "70", curHlc, "dev-a", "75", inHlc, "dev-b");
    }

    @Test
    void laterIncomingWins() {
        MergeOutcome out = strategy.merge(input(hlc(1, 0, "a"), hlc(2, 0, "b")));
        assertFalse(out.conflict());
        assertEquals("75", out.resolvedValue());
    }

    @Test
    void earlierIncomingIsIgnored() {
        MergeOutcome out = strategy.merge(input(hlc(2, 0, "a"), hlc(1, 0, "b")));
        assertFalse(out.conflict());
        assertEquals("70", out.resolvedValue());
    }

    @Test
    void identicalTimestampKeepsCurrent() {
        MergeOutcome out = strategy.merge(input(hlc(1, 0, "a"), hlc(1, 0, "a")));
        assertEquals("70", out.resolvedValue());
    }

    @Test
    void counterBreaksTiesWithinTheSameMillisecond() {
        MergeOutcome out = strategy.merge(input(hlc(1, 1, "a"), hlc(1, 2, "b")));
        assertEquals("75", out.resolvedValue());
    }

    @Test
    void nodeIdBreaksFinalTiesSoEveryReplicaPicksTheSameWinner() {
        String x = hlc(1, 1, "a");
        String y = hlc(1, 1, "b");
        MergeInput forward = new MergeInput("w", "X", x, "a", "Y", y, "b");
        MergeInput backward = new MergeInput("w", "Y", y, "b", "X", x, "a");
        assertEquals(strategy.merge(forward).resolvedValue(), strategy.merge(backward).resolvedValue());
    }

    @Test
    void neverReportsAConflict() {
        assertFalse(strategy.merge(input(hlc(1, 0, "a"), hlc(1, 0, "b"))).conflict());
    }
}

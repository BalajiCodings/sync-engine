package com.balaji.sync_engine.clock;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HLCTimestampTest {

    @Test
    void toStringIsFixedWidthSoStringOrderEqualsTimeOrder() {
        HLCTimestamp small = new HLCTimestamp(999L, 0, "a");
        HLCTimestamp big = new HLCTimestamp(1_000L, 0, "a");

        assertEquals(19 + 1 + 10 + 1 + 1, small.toString().length());
        assertTrue(small.toString().compareTo(big.toString()) < 0,
                "padded strings must sort like the timestamps (Postgres compares them as text)");
    }

    @Test
    void parseIsTheInverseOfToString() {
        HLCTimestamp original = new HLCTimestamp(1_700_000_000_123L, 7, "device-1");
        assertEquals(original, HLCTimestamp.parse(original.toString()));
    }

    @Test
    void nodeIdContainingDashesSurvivesRoundTrip() {
        HLCTimestamp original = new HLCTimestamp(5L, 1, "tablet-ward-3");
        assertEquals("tablet-ward-3", HLCTimestamp.parse(original.toString()).nodeId());
    }

    @Test
    void parseRejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> HLCTimestamp.parse("not-a-timestamp"));
        assertThrows(IllegalArgumentException.class, () -> HLCTimestamp.parse("123"));
    }

    @Test
    void ordersByPhysicalThenCounterThenNode() {
        assertTrue(new HLCTimestamp(1, 9, "z").compareTo(new HLCTimestamp(2, 0, "a")) < 0);
        assertTrue(new HLCTimestamp(2, 1, "z").compareTo(new HLCTimestamp(2, 2, "a")) < 0);
        assertTrue(new HLCTimestamp(2, 2, "a").compareTo(new HLCTimestamp(2, 2, "b")) < 0);
        assertEquals(0, new HLCTimestamp(2, 2, "a").compareTo(new HLCTimestamp(2, 2, "a")));
    }

    @Test
    void equalsAndHashCodeAgree() {
        HLCTimestamp a = new HLCTimestamp(1, 2, "n");
        HLCTimestamp b = new HLCTimestamp(1, 2, "n");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new HLCTimestamp(1, 3, "n"));
    }
}

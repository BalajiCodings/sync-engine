package com.balaji.sync_engine.crdt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PnCounterTest {

    private static PnCounter inc(String node, long n) {
        return new PnCounter(Map.of(node, n), Map.of());
    }

    @Test
    void concurrentIncrementsFromDifferentDevicesBothCount() {
        assertEquals(5, inc("deviceA", 2).merge(inc("deviceB", 3)).value());
    }

    @Test
    void mergeIsCommutative() {
        PnCounter a = inc("deviceA", 2), b = inc("deviceB", 3);
        assertEquals(a.merge(b), b.merge(a));
    }

    @Test
    void mergeIsAssociative() {
        PnCounter a = inc("deviceA", 2), b = inc("deviceB", 3), c = inc("deviceC", 7);
        assertEquals(a.merge(b).merge(c), a.merge(b.merge(c)));
    }

    @Test
    void mergeIsIdempotent() {
        PnCounter a = inc("deviceA", 4);
        assertEquals(a, a.merge(a));
    }

    @Test
    void staleStateCannotRewindACounter() {
        assertEquals(5, inc("deviceA", 5).merge(inc("deviceA", 3)).value());
    }

    @Test
    void decrementsSubtractFromTheTotal() {
        PnCounter corrected = new PnCounter(Map.of("deviceA", 2L), Map.of("deviceA", 1L));
        assertEquals(4, corrected.merge(inc("deviceB", 3)).value());
    }

    @Test
    void rejectsNegativeEntries() {
        assertThrows(IllegalArgumentException.class,
                () -> new PnCounter(Map.of("deviceA", -1L), Map.of()));
    }
}
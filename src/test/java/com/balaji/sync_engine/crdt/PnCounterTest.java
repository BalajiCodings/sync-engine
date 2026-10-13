package com.balaji.sync_engine.crdt;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PnCounterTest {

    private static PnCounter of(Map<String, Long> inc, Map<String, Long> dec) {
        return new PnCounter(inc, dec);
    }

    private static PnCounter random(Random r) {
        Map<String, Long> inc = new HashMap<>();
        Map<String, Long> dec = new HashMap<>();
        for (String node : new String[] {"a", "b", "c"}) {
            if (r.nextBoolean()) inc.put(node, (long) r.nextInt(50));
            if (r.nextBoolean()) dec.put(node, (long) r.nextInt(50));
        }
        return of(inc, dec);
    }

    @Test
    void emptyCounterIsZero() {
        assertEquals(0, PnCounter.empty().value());
    }

    @Test
    void valueIsIncrementsMinusDecrements() {
        assertEquals(7, of(Map.of("a", 10L, "b", 2L), Map.of("a", 5L)).value());
    }

    @Test
    void mergeTakesThePerNodeMaximumNotTheSum() {
        PnCounter x = of(Map.of("a", 5L), Map.of());
        PnCounter y = of(Map.of("a", 3L, "b", 4L), Map.of());
        assertEquals(9, x.merge(y).value()); // max(5,3)+4, not 5+3+4
    }

    @Test
    void mergingTheSameStateTwiceChangesNothing() {
        PnCounter x = of(Map.of("a", 5L), Map.of("b", 1L));
        assertEquals(x, x.merge(x));
    }

    @Test
    void mergeIsCommutativeAssociativeAndIdempotent_overManyRandomStates() {
        Random r = new Random(42); // seeded: a failure is reproducible
        for (int i = 0; i < 500; i++) {
            PnCounter a = random(r), b = random(r), c = random(r);
            assertEquals(a.merge(b), b.merge(a), "commutative");
            assertEquals(a.merge(b).merge(c), a.merge(b.merge(c)), "associative");
            assertEquals(a, a.merge(a), "idempotent");
        }
    }

    @Test
    void replicasConvergeRegardlessOfMergeOrder() {
        PnCounter a = of(Map.of("a", 3L), Map.of());
        PnCounter b = of(Map.of("b", 4L), Map.of("b", 1L));
        PnCounter c = of(Map.of("c", 2L), Map.of());

        PnCounter one = a.merge(b).merge(c);
        PnCounter two = c.merge(a).merge(b);
        assertEquals(one, two);
        assertEquals(8, one.value());
    }

    @Test
    void equalStatesAreEqualWhateverInsertionOrder() {
        Map<String, Long> first = new java.util.LinkedHashMap<>();
        first.put("b", 1L);
        first.put("a", 2L);
        Map<String, Long> second = new java.util.LinkedHashMap<>();
        second.put("a", 2L);
        second.put("b", 1L);
        assertEquals(of(first, Map.of()), of(second, Map.of()));
    }

    @Test
    void rejectsNegativeAndBlankEntries() {
        assertThrows(IllegalArgumentException.class, () -> of(Map.of("a", -1L), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> of(Map.of(" ", 1L), Map.of()));
    }

    @Test
    void stateIsImmutable() {
        PnCounter x = of(Map.of("a", 1L), Map.of());
        assertThrows(UnsupportedOperationException.class, () -> x.increments().put("b", 1L));
    }

    @Test
    void overflowIsDetectedNotWrapped() {
        PnCounter huge = of(Map.of("a", Long.MAX_VALUE, "b", 1L), Map.of());
        assertThrows(ArithmeticException.class, huge::value);
    }

    @Test
    void nodesListsEveryoneWhoEverWrote() {
        PnCounter x = of(Map.of("a", 1L), Map.of("b", 1L));
        assertEquals(java.util.Set.of("a", "b"), x.nodes());
    }
}

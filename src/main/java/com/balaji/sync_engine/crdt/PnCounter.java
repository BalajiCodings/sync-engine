package com.balaji.sync_engine.crdt;

import java.util.*;

/**
 * PN-Counter: a state-based CRDT. Each node owns one entry in each map.
 * merge() takes the per-node maximum, so merges are commutative, associative and idempotent.
 */
public record PnCounter(Map<String, Long> increments, Map<String, Long> decrements) {

    public PnCounter {
        increments = canonical(increments, "increments");
        decrements = canonical(decrements, "decrements");
    }

    public static PnCounter empty() {
        return new PnCounter(Map.of(), Map.of());
    }

    public PnCounter merge(PnCounter other) {
        return new PnCounter(maxPerNode(increments, other.increments),
                             maxPerNode(decrements, other.decrements));
    }

    public long value() {
        return sum(increments) - sum(decrements);
    }

    public Set<String> nodes() {
        Set<String> all = new TreeSet<>(increments.keySet());
        all.addAll(decrements.keySet());
        return all;
    }

    private static Map<String, Long> maxPerNode(Map<String, Long> a, Map<String, Long> b) {
        Map<String, Long> merged = new HashMap<>(a);
        b.forEach((node, count) -> merged.merge(node, count, Math::max));
        return merged;
    }

    private static long sum(Map<String, Long> counts) {
        long total = 0;
        for (long count : counts.values()) {
            total = Math.addExact(total, count);   // fail loudly instead of silently wrapping
        }
        return total;
    }

    /** Sorted copy so equal states serialize identically; rejects values that would break monotonicity. */
    private static Map<String, Long> canonical(Map<String, Long> input, String label) {
        TreeMap<String, Long> sorted = new TreeMap<>();
        if (input != null) {
            input.forEach((node, count) -> {
                if (node == null || node.isBlank()) {
                    throw new IllegalArgumentException(label + " contains a blank device id");
                }
                if (count == null || count < 0) {
                    throw new IllegalArgumentException(
                            label + " entry for '" + node + "' must be a non-negative number");
                }
                sorted.put(node, count);
            });
        }
        return Collections.unmodifiableSortedMap(sorted);
    }
}
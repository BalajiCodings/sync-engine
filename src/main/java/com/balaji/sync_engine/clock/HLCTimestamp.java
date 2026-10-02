package com.balaji.sync_engine.clock;

import java.util.Objects;

public final class HLCTimestamp implements Comparable<HLCTimestamp> {

    private final long physicalTime;
    private final int counter;
    private final String nodeId;

    public HLCTimestamp(long physicalTime, int counter, String nodeId) {
        this.physicalTime = physicalTime;
        this.counter = counter;
        this.nodeId = nodeId;
    }

    public long physicalTime() {
        return physicalTime;
    }

    public int counter() {
        return counter;
    }

    public String nodeId() {
        return nodeId;
    }

    @Override
    public int compareTo(HLCTimestamp other) {
        int physicalCompare = Long.compare(this.physicalTime, other.physicalTime);
        if (physicalCompare != 0) {
            return physicalCompare;
        }
        int counterCompare = Integer.compare(this.counter, other.counter);
        if (counterCompare != 0) {
            return counterCompare;
        }
        // Tie-break on nodeId purely for total ordering/determinism —
        // this does NOT imply true causal precedence between concurrent events.
        return this.nodeId.compareTo(other.nodeId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HLCTimestamp that)) return false;
        return physicalTime == that.physicalTime
                && counter == that.counter
                && nodeId.equals(that.nodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(physicalTime, counter, nodeId);
    }

    @Override
    public String toString() {
        return physicalTime + "-" + counter + "-" + nodeId;
    }

    public static HLCTimestamp parse(String encoded) {
        String[] parts = encoded.split("-", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid HLC timestamp format: " + encoded);
        }
        return new HLCTimestamp(Long.parseLong(parts[0]), Integer.parseInt(parts[1]), parts[2]);
    }
}
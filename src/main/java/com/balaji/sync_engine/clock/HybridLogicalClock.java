package com.balaji.sync_engine.clock;

public class HybridLogicalClock {

    private long physicalTime;
    private int counter;
    private final String nodeId;

    public HybridLogicalClock(String nodeId) {
        this.nodeId = nodeId;
        this.physicalTime = 0L;
        this.counter = 0;
    }

    /**
     * Called when a LOCAL event occurs on this node (e.g., this device creates a change event).
     */
    public synchronized HLCTimestamp tick() {
        long now = System.currentTimeMillis();

        if (now > physicalTime) {
            physicalTime = now;
            counter = 0;
        } else {
            counter++;
        }

        return new HLCTimestamp(physicalTime, counter, nodeId);
    }

    /**
     * Called when this node RECEIVES a timestamp from elsewhere
     * (e.g., server receiving a client event, or client receiving server events on pull).
     */
    public synchronized HLCTimestamp update(HLCTimestamp received) {
        long now = System.currentTimeMillis();
        long maxPhysical = Math.max(now, Math.max(physicalTime, received.physicalTime()));

        if (maxPhysical == physicalTime && maxPhysical == received.physicalTime()) {
            counter = Math.max(counter, received.counter()) + 1;
        } else if (maxPhysical == physicalTime) {
            counter = counter + 1;
        } else if (maxPhysical == received.physicalTime()) {
            counter = received.counter() + 1;
        } else {
            counter = 0;
        }

        physicalTime = maxPhysical;
        return new HLCTimestamp(physicalTime, counter, nodeId);
    }
}
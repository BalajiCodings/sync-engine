package com.balaji.sync_engine.conflict;

import com.balaji.sync_engine.crdt.PnCounter;
import com.balaji.sync_engine.crdt.PnCounterCodec;

public class PnCounterMergeStrategy implements FieldMergeStrategy {

    private final PnCounterCodec codec;

    public PnCounterMergeStrategy(PnCounterCodec codec) {
        this.codec = codec;
    }

    @Override
    public MergeOutcome merge(MergeInput input) {
        PnCounter incoming = parseOwnEntryOnly(input.incomingValue(), input.incomingDeviceId());
        PnCounter current = input.currentValue() == null
                ? PnCounter.empty()
                : codec.parse(input.currentValue());

        PnCounter merged = current.merge(incoming);
        return MergeOutcome.resolved(codec.write(merged),
                "Counter states merged (per-device maximum); concurrent updates cannot conflict");
    }

    @Override
    public String onFirstWrite(String fieldName, String incomingValue, String incomingDeviceId) {
        return codec.write(parseOwnEntryOnly(incomingValue, incomingDeviceId));
    }

    /** A max-merge can never lower a value, so a forged foreign entry would inflate the total forever. */
    private PnCounter parseOwnEntryOnly(String json, String deviceId) {
        PnCounter counter = codec.parse(json);
        for (String node : counter.nodes()) {
            if (!node.equals(deviceId)) {
                throw new IllegalArgumentException("Counter state from device '" + deviceId
                        + "' may only contain its own entry, found '" + node + "'");
            }
        }
        return counter;
    }
}
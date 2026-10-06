package com.balaji.sync_engine.conflict;

import com.balaji.sync_engine.clock.HLCTimestamp;

public class LastWriteWinsStrategy implements FieldMergeStrategy {

    @Override
    public MergeOutcome merge(MergeInput input) {
        HLCTimestamp current = HLCTimestamp.parse(input.currentHlc());
        HLCTimestamp incoming = HLCTimestamp.parse(input.incomingHlc());

        if (incoming.compareTo(current) > 0) {
            return MergeOutcome.resolved(input.incomingValue(),
                    "Incoming write is causally/logically later — accepted");
        }
        return MergeOutcome.resolved(input.currentValue(),
                "Current value is already later or equal — incoming write ignored");
    }
}
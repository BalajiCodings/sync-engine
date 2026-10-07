package com.balaji.sync_engine.conflict;

import com.balaji.sync_engine.clock.HLCTimestamp;

public class ManualReviewStrategy implements FieldMergeStrategy {

    @Override
    public MergeOutcome merge(MergeInput input) {
        boolean sameDevice = input.currentDeviceId() != null
                && input.currentDeviceId().equals(input.incomingDeviceId());

        if (sameDevice) {
            // Sequential edits from the same device/lineage carry an implicit causal
            // relationship -- there's no second party to be "concurrent" with.
            // We can safely apply ordinary last-write-wins here without human review.
            HLCTimestamp current = HLCTimestamp.parse(input.currentHlc());
            HLCTimestamp incoming = HLCTimestamp.parse(input.incomingHlc());

            if (incoming.compareTo(current) > 0) {
                return MergeOutcome.resolved(input.incomingValue(),
                        "Same-device sequential update to a protected field — no review needed");
            }
            return MergeOutcome.resolved(input.currentValue(),
                    "Same-device out-of-order update ignored (already-later value retained)");
        }

        return MergeOutcome.conflicted(
                "Field '" + input.fieldName() + "' edited by two different devices "
                + "(" + input.currentDeviceId() + " and " + input.incomingDeviceId() + ") — "
                + "requires manual review (current=" + input.currentValue()
                + ", incoming=" + input.incomingValue() + ")"
        );
    }
}
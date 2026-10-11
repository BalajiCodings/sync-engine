package com.balaji.sync_engine.conflict;

import com.balaji.sync_engine.clock.HLCTimestamp;

public class ManualReviewStrategy implements FieldMergeStrategy {

    @Override
    public MergeOutcome merge(MergeInput input) {
        boolean sameDevice = input.currentDeviceId() != null
                && input.currentDeviceId().equals(input.incomingDeviceId());

        if (sameDevice) {
            HLCTimestamp current = HLCTimestamp.parse(input.currentHlc());
            HLCTimestamp incoming = HLCTimestamp.parse(input.incomingHlc());

            if (incoming.compareTo(current) > 0) {
                return MergeOutcome.resolved(input.incomingValue(),
                        "Same-device sequential update to a protected field — no review needed");
            }
            return MergeOutcome.resolved(input.currentValue(),
                    "Same-device out-of-order update ignored (already-later value retained)");
        }

        if (input.incomingObservedCurrent()) {
            return MergeOutcome.resolved(input.incomingValue(),
                    "Edit made after the writer had received the current value — sequential update, no review needed");
        }

        return MergeOutcome.conflicted(
                "Field '" + input.fieldName() + "' edited by two different devices ("
                + input.currentDeviceId() + " and " + input.incomingDeviceId() + ") and the second had not "
                + "received the first — requires manual review (current=" + input.currentValue()
                + ", incoming=" + input.incomingValue() + ")"
        );
    }
}
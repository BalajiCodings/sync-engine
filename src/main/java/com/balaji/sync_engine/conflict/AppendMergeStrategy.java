package com.balaji.sync_engine.conflict;

import com.balaji.sync_engine.clock.HLCTimestamp;

public class AppendMergeStrategy implements FieldMergeStrategy {

    private static final String SEPARATOR = "\n---\n";

    @Override
    public MergeOutcome merge(MergeInput input) {
        String current = input.currentValue() == null ? "" : input.currentValue();
        String incoming = input.incomingValue() == null ? "" : input.incomingValue();

        if (current.equals(incoming)) {
            return MergeOutcome.resolved(current, "Identical content — no merge needed");
        }
        if (current.isBlank()) {
            return MergeOutcome.resolved(incoming, "Current was empty — incoming adopted");
        }
        if (incoming.isBlank()) {
            return MergeOutcome.resolved(current, "Incoming was empty — current retained");
        }

        boolean currentFirst = isCurrentFirst(input.currentHlc(), input.incomingHlc(), current, incoming);
        String merged = currentFirst
                ? current + SEPARATOR + incoming
                : incoming + SEPARATOR + current;
        return MergeOutcome.resolved(merged, "Concurrent edits appended deterministically");
    }

    private boolean isCurrentFirst(String currentHlc, String incomingHlc, String current, String incoming) {
        try {
            if (currentHlc != null && incomingHlc != null) {
                int cmp = HLCTimestamp.parse(currentHlc).compareTo(HLCTimestamp.parse(incomingHlc));
                if (cmp != 0) {
                    return cmp < 0;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Fall back to lexical comparison if non-HLC strings were passed in unit tests
        }
        return current.compareTo(incoming) <= 0;
    }
}
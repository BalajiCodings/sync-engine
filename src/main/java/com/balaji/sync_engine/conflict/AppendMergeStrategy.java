package com.balaji.sync_engine.conflict;

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

        String merged = current + SEPARATOR + incoming;
        return MergeOutcome.resolved(merged, "Concurrent edits appended rather than one overwriting the other");
    }
}
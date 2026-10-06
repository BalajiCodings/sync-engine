package com.balaji.sync_engine.conflict;

public record MergeOutcome(
        boolean conflict,
        String resolvedValue,
        String reason
) {
    public static MergeOutcome resolved(String value, String reason) {
        return new MergeOutcome(false, value, reason);
    }

    public static MergeOutcome conflicted(String reason) {
        return new MergeOutcome(true, null, reason);
    }
}
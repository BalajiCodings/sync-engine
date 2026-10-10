package com.balaji.sync_engine.conflict;

public interface FieldMergeStrategy {

    MergeOutcome merge(MergeInput input);

    /**
     * Called for the first-ever write of a field, when there is nothing to merge against.
     * Strategies may validate or canonicalize. Default: accept as-is.
     */
    default String onFirstWrite(String fieldName, String incomingValue, String incomingDeviceId) {
        return incomingValue;
    }
}
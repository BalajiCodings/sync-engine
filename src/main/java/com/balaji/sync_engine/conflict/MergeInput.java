package com.balaji.sync_engine.conflict;

public record MergeInput(
        String fieldName,
        String currentValue,
        String currentHlc,
        String currentDeviceId,
        String incomingValue,
        String incomingHlc,
        String incomingDeviceId,
        boolean incomingObservedCurrent
) {
    /** Legacy form with no causality information: a write from a different device counts as concurrent. */
    public MergeInput(String fieldName, String currentValue, String currentHlc, String currentDeviceId,
                      String incomingValue, String incomingHlc, String incomingDeviceId) {
        this(fieldName, currentValue, currentHlc, currentDeviceId,
                incomingValue, incomingHlc, incomingDeviceId, false);
    }
}
package com.balaji.sync_engine.conflict;

public record MergeInput(
        String fieldName,
        String currentValue,
        String currentHlc,
        String incomingValue,
        String incomingHlc
) {}
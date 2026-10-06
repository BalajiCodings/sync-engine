package com.balaji.sync_engine.conflict;

public interface FieldMergeStrategy {

    MergeOutcome merge(MergeInput input);
}
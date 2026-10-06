package com.balaji.sync_engine.conflict;

public class ManualReviewStrategy implements FieldMergeStrategy {

    @Override
    public MergeOutcome merge(MergeInput input) {
        return MergeOutcome.conflicted(
                "Field '" + input.fieldName() + "' requires manual review: concurrent edit detected "
                + "(current=" + input.currentValue() + ", incoming=" + input.incomingValue() + ")"
        );
    }
}
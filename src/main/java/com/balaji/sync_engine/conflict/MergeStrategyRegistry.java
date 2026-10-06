package com.balaji.sync_engine.conflict;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MergeStrategyRegistry {

    private final Map<String, FieldMergeStrategy> fieldStrategies = Map.of(
            "weight", new LastWriteWinsStrategy(),
            "bloodPressure", new LastWriteWinsStrategy(),
            "dosage", new ManualReviewStrategy()
    );

    private final FieldMergeStrategy defaultStrategy = new LastWriteWinsStrategy();

    public FieldMergeStrategy strategyFor(String fieldName) {
        return fieldStrategies.getOrDefault(fieldName, defaultStrategy);
    }
}
package com.balaji.sync_engine.conflict;

import com.balaji.sync_engine.crdt.PnCounterCodec;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MergeStrategyRegistry {

    private final Map<String, FieldMergeStrategy> fieldStrategies;
    private final FieldMergeStrategy defaultStrategy = new LastWriteWinsStrategy();

    public MergeStrategyRegistry(PnCounterCodec counterCodec) {
        this.fieldStrategies = Map.of(
                "weight", new LastWriteWinsStrategy(),
                "bloodPressure", new LastWriteWinsStrategy(),
                "dosage", new ManualReviewStrategy(),
                "dosesAdministered", new PnCounterMergeStrategy(counterCodec)
        );
    }

    public FieldMergeStrategy strategyFor(String fieldName) {
        return fieldStrategies.getOrDefault(fieldName, defaultStrategy);
    }
}
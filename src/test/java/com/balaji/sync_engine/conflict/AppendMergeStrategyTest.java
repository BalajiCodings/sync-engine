package com.balaji.sync_engine.conflict;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AppendMergeStrategyTest {

    private final AppendMergeStrategy strategy = new AppendMergeStrategy();

    private MergeOutcome merge(String current, String incoming) {
        return strategy.merge(new MergeInput("notes", current, "h1", "a", incoming, "h2", "b"));
    }

    @Test
    void differentTextIsKeptFromBothSides() {
        MergeOutcome out = merge("fever", "cough");
        assertFalse(out.conflict());
        assertEquals("fever\n---\ncough", out.resolvedValue());
    }

    @Test
    void identicalTextIsNotDuplicated() {
        assertEquals("same", merge("same", "same").resolvedValue());
    }

    @Test
    void emptyCurrentAdoptsIncoming() {
        assertEquals("new", merge("", "new").resolvedValue());
        assertEquals("new", merge(null, "new").resolvedValue());
    }

    @Test
    void emptyIncomingKeepsCurrent() {
        assertEquals("old", merge("old", "  ").resolvedValue());
        assertEquals("old", merge("old", null).resolvedValue());
    }
}

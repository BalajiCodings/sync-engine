package com.balaji.sync_engine.crdt;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PnCounterCodec {

    private final JsonMapper jsonMapper;

    public PnCounterCodec(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public PnCounter parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Invalid counter state: a value is required");
        }
        try {
            return jsonMapper.readValue(json, PnCounter.class);
        } catch (JacksonException ex) {
            String detail = ex.getCause() instanceof IllegalArgumentException iae
                    ? iae.getMessage()
                    : "expected {\"increments\":{\"<deviceId>\":n},\"decrements\":{...}}";
            throw new IllegalArgumentException("Invalid counter state: " + detail, ex);
        }
    }

    public String write(PnCounter counter) {
        return jsonMapper.writeValueAsString(counter);
    }
}
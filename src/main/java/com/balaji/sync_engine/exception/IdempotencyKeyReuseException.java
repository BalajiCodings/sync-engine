package com.balaji.sync_engine.exception;

public class IdempotencyKeyReuseException extends RuntimeException {
    public IdempotencyKeyReuseException(String key) {
        super("Idempotency-Key '" + key + "' was already used with a different request. "
                + "Use a new key for a new request.");
    }
}
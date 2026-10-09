package com.balaji.sync_engine.exception;

import java.util.UUID;

public class ConflictAlreadyResolvedException extends RuntimeException {
    public ConflictAlreadyResolvedException(UUID id) {
        super("Conflict " + id + " has already been resolved");
    }
}
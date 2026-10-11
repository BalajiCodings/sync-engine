package com.balaji.sync_engine.exception;

public class InvalidStreamTicketException extends RuntimeException {
    public InvalidStreamTicketException() {
        super("Stream ticket is invalid, expired, or already used");
    }
}
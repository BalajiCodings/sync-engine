package com.balaji.sync_engine.dto;

public enum ErrorCode {
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    IDEMPOTENCY_KEY_REUSED,
    RATE_LIMIT_EXCEEDED,
    INTERNAL_ERROR;

    public static ErrorCode fromStatus(int status) {
        return switch (status) {
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 409 -> CONFLICT;
            case 429 -> RATE_LIMIT_EXCEEDED;
            default -> status >= 500 ? INTERNAL_ERROR : BAD_REQUEST;
        };
    }
}
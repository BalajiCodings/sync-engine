package com.balaji.sync_engine.dto;

public enum ErrorCode {
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    INTERNAL_ERROR;

    public static ErrorCode fromStatus(int status) {
        return switch (status) {
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            default -> status >= 500 ? INTERNAL_ERROR : BAD_REQUEST;
        };
    }
}
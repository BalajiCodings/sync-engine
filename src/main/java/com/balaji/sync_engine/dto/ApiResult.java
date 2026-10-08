package com.balaji.sync_engine.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResult<T>(
        @Schema(description = "True if the request itself was processed. Per-item outcomes (e.g. rejected sync events) are inside data.")
        boolean success,
        @Schema(description = "Response payload. Absent on failure.")
        T data,
        @Schema(description = "Error details. Absent on success.")
        ApiError error,
        Instant timestamp
) {
    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(true, data, null, Instant.now());
    }

    public static ApiResult<Void> empty() {
        return new ApiResult<>(true, null, null, Instant.now());
    }

    public static <T> ApiResult<T> failure(ErrorCode code, String message) {
        return new ApiResult<>(false, null, new ApiError(code, message), Instant.now());
    }
}
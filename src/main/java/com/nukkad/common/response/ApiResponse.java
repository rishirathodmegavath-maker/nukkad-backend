package com.nukkad.common.response;

import java.time.Instant;

public record ApiResponse<T>(boolean success, T data, String message) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message);
    }

    public record Error(boolean success, String message, String errorCode, Instant timestamp, String path) {
        public static Error of(String message, String errorCode, String path) {
            return new Error(false, message, errorCode, Instant.now(), path);
        }
    }
}

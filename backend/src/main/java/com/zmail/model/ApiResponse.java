package com.zmail.model;

import java.time.OffsetDateTime;

public record ApiResponse<T>(T data, String error, OffsetDateTime timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, null, OffsetDateTime.now());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(null, null, OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(null, message, OffsetDateTime.now());
    }
}
package com.fingermemory.server.common;

import java.util.List;

public record ApiError(String code, String message, List<FieldError> fieldErrors) {
    public ApiError(String code, String message) {
        this(code, message, List.of());
    }

    public record FieldError(String field, String message) {
    }
}

package com.karaogui.backend.error;

import java.util.Map;

public record ApiError(String code, String message, Map<String, Object> details) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Map.of());
    }

    public record Envelope(ApiError error) {}

    public Envelope wrap() {
        return new Envelope(this);
    }
}

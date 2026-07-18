package com.karaogui.backend.auth;

public class GameScopeException extends RuntimeException {

    private final String code;

    public GameScopeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

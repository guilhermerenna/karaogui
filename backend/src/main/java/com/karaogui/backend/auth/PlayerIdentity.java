package com.karaogui.backend.auth;

import java.util.UUID;

public record PlayerIdentity(UUID playerId, UUID gameId, boolean displayToken) {

    public boolean isDisplayToken() {
        return displayToken;
    }
}

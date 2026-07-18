package com.karaogui.backend.auth;

import java.util.UUID;

public final class ScopeGuard {

    private ScopeGuard() {}

    public static void requireScope(PlayerIdentity identity, UUID gameId) {
        if (!gameId.equals(identity.gameId())) {
            throw new GameScopeException("FORBIDDEN", "Token is not valid for this game.");
        }
    }

    public static void requirePlayerScope(PlayerIdentity identity, UUID gameId) {
        requireScope(identity, gameId);
        if (identity.isDisplayToken()) {
            throw new GameScopeException("DISPLAY_TOKEN_FORBIDDEN",
                    "Display token cannot invoke commands.");
        }
    }
}

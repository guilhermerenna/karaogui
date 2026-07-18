package com.karaogui.backend.auth;

import java.security.Principal;
import java.util.UUID;

public record StompPrincipal(
        UUID playerId,
        UUID gameId,
        String surface,
        boolean displayOnly,
        String pendingTokenHash
) implements Principal {

    public StompPrincipal(UUID playerId, UUID gameId, String surface, boolean displayOnly) {
        this(playerId, gameId, surface, displayOnly, null);
    }

    @Override
    public String getName() {
        if (pendingTokenHash != null) return pendingTokenHash;
        return playerId != null ? playerId.toString() : "display:" + gameId;
    }
}

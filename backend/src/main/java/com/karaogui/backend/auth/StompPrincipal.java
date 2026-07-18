package com.karaogui.backend.auth;

import java.security.Principal;
import java.util.UUID;

public record StompPrincipal(
        UUID playerId,
        UUID gameId,
        String surface,
        boolean displayOnly
) implements Principal {

    @Override
    public String getName() {
        return playerId != null ? playerId.toString() : "display:" + gameId;
    }
}

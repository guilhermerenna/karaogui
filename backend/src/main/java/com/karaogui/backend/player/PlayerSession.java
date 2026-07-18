package com.karaogui.backend.player;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "player_session")
public class PlayerSession {

    @Id
    private UUID id;

    @Column(name = "player_id", nullable = false, unique = true)
    private UUID playerId;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PlayerSession() {}

    public PlayerSession(UUID id, UUID playerId, UUID gameId, String tokenHash, Instant createdAt) {
        this.id = id;
        this.playerId = playerId;
        this.gameId = gameId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getPlayerId() { return playerId; }
    public UUID getGameId() { return gameId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getCreatedAt() { return createdAt; }
}

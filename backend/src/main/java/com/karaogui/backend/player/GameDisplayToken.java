package com.karaogui.backend.player;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game_display_token")
public class GameDisplayToken {

    @Id
    private UUID id;

    @Column(name = "game_id", nullable = false, unique = true)
    private UUID gameId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GameDisplayToken() {}

    public GameDisplayToken(UUID id, UUID gameId, String tokenHash, Instant createdAt) {
        this.id = id;
        this.gameId = gameId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getGameId() { return gameId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getCreatedAt() { return createdAt; }
}

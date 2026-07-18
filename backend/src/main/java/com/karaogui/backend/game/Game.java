package com.karaogui.backend.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game")
public class Game {

    @Id
    private UUID id;

    @Column(name = "join_code", length = 6, nullable = false, unique = true)
    private String joinCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameState state;

    @Column(name = "host_player_id")
    private UUID hostPlayerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "game_seq", nullable = false)
    private long gameSeq = 0;

    protected Game() {}

    public Game(UUID id, String joinCode, GameState state, UUID hostPlayerId, Instant createdAt) {
        this.id = id;
        this.joinCode = joinCode;
        this.state = state;
        this.hostPlayerId = hostPlayerId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getJoinCode() { return joinCode; }
    public GameState getState() { return state; }
    public UUID getHostPlayerId() { return hostPlayerId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }

    public void setHostPlayerId(UUID hostPlayerId) { this.hostPlayerId = hostPlayerId; }
    public void setState(GameState state) { this.state = state; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }

    public long getGameSeq() { return gameSeq; }

    public long incrementAndGetSeq() {
        this.gameSeq += 1;
        return this.gameSeq;
    }
}

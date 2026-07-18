package com.karaogui.backend.tv;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "pending_tv_session")
public class PendingTvSession {

    @Id
    @Column(name = "join_code", length = 6, nullable = false)
    private String joinCode;

    @Column(name = "display_token_hash", nullable = false, unique = true)
    private String displayTokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PendingTvSession() {}

    public PendingTvSession(String joinCode, String displayTokenHash, Instant createdAt) {
        this.joinCode = joinCode;
        this.displayTokenHash = displayTokenHash;
        this.createdAt = createdAt;
    }

    public String getJoinCode() { return joinCode; }
    public String getDisplayTokenHash() { return displayTokenHash; }
    public Instant getCreatedAt() { return createdAt; }
}

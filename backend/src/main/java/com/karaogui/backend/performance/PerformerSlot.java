package com.karaogui.backend.performance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "performer_slot")
public class PerformerSlot {

    @Id
    private UUID id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "performance_id", nullable = false)
    private Long performanceId;

    @Column(name = "slot_index", nullable = false)
    private int slotIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlotOrigin origin;

    @Column(name = "original_player_id")
    private UUID originalPlayerId;

    @Column(name = "current_player_id")
    private UUID currentPlayerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlotState state;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Version
    private long version;

    protected PerformerSlot() {}

    public PerformerSlot(UUID id, UUID gameId, Long performanceId, int slotIndex,
            SlotOrigin origin, UUID playerId) {
        this.id = id;
        this.gameId = gameId;
        this.performanceId = performanceId;
        this.slotIndex = slotIndex;
        this.origin = origin;
        this.originalPlayerId = playerId;
        this.currentPlayerId = playerId;
        this.state = SlotState.PENDING;
    }

    public UUID getId() { return id; }
    public UUID getGameId() { return gameId; }
    public Long getPerformanceId() { return performanceId; }
    public int getSlotIndex() { return slotIndex; }
    public SlotOrigin getOrigin() { return origin; }
    public UUID getOriginalPlayerId() { return originalPlayerId; }
    public UUID getCurrentPlayerId() { return currentPlayerId; }
    public SlotState getState() { return state; }
    public Instant getConfirmedAt() { return confirmedAt; }

    public void setState(SlotState state) { this.state = state; }
    public void setCurrentPlayerId(UUID currentPlayerId) { this.currentPlayerId = currentPlayerId; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
}

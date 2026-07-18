package com.karaogui.backend.game;

import com.karaogui.backend.game.dto.RankingPageDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GameEvent(long seq, String type, Instant at, Object data) {

    public record PlayerJoinedData(UUID playerId, String displayName) {}

    public record GameStartedData(UUID gameId) {}

    public record RankingUpdatedData(List<RankingPageDto.Entry> entries, int totalPlayers) {}

    public record SlotInfo(UUID slotId, int slotIndex, UUID currentPlayerId, String currentPlayerName, String state) {}

    public record ScoreResult(UUID playerId, String displayName, int points) {}

    public record PerformanceAnnouncedData(Long performanceId, String type, List<SlotInfo> slots,
            List<String> judgeNames, List<UUID> judgePlayerIds, String youtubeUrl, Instant confirmDeadlineAt) {}

    public record SlotStateChangedData(Long performanceId, UUID slotId, String slotState,
            String currentPlayerName) {}

    public record PerformanceStartedData(Long performanceId) {}

    public record PerformanceLockedData(Long performanceId, List<ScoreResult> scores) {}

    public record PerformanceSkippedData(Long performanceId) {}

    public record GameEndedData(String gameId) {}

    public record CommentPostedData(java.util.UUID commentId, java.util.UUID authorPlayerId,
        String authorName, String body, java.time.Instant createdAt) {}

    public record CommentLikedData(java.util.UUID commentId, int likeCount) {}
}

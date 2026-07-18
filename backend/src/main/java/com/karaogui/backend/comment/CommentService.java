package com.karaogui.backend.comment;

import com.karaogui.backend.auth.PlayerIdentity;
import com.karaogui.backend.auth.ScopeGuard;
import com.karaogui.backend.comment.dto.CommentDto;
import com.karaogui.backend.comment.dto.PostCommentRequest;
import com.karaogui.backend.error.GameStateException;
import com.karaogui.backend.game.Game;
import com.karaogui.backend.game.GameDomainEvent;
import com.karaogui.backend.game.GameRepository;
import com.karaogui.backend.game.GameState;
import com.karaogui.backend.player.Player;
import com.karaogui.backend.player.PlayerRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    private static final int MAX_BODY_LENGTH = 280;
    private static final int PAGE_SIZE = 20;

    private final CommentRepository commentRepo;
    private final CommentLikeRepository likeRepo;
    private final GameRepository gameRepo;
    private final PlayerRepository playerRepo;
    private final ApplicationEventPublisher eventPublisher;

    public CommentService(CommentRepository commentRepo, CommentLikeRepository likeRepo,
            GameRepository gameRepo, PlayerRepository playerRepo,
            ApplicationEventPublisher eventPublisher) {
        this.commentRepo = commentRepo;
        this.likeRepo = likeRepo;
        this.gameRepo = gameRepo;
        this.playerRepo = playerRepo;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CommentDto postComment(UUID gameId, PlayerIdentity identity, PostCommentRequest req) {
        ScopeGuard.requirePlayerScope(identity, gameId);
        Game game = requireGame(gameId);
        if (game.getState() != GameState.ACTIVE) {
            throw new GameStateException("GAME_NOT_ACTIVE", "Game must be ACTIVE to post comments.");
        }
        String body = req.body() == null ? "" : req.body().trim();
        if (body.isEmpty() || body.length() > MAX_BODY_LENGTH) {
            throw new GameStateException("INVALID_BODY", "Comment must be 1–280 characters.");
        }
        Player author = requirePlayer(identity.playerId(), gameId);
        Instant now = Instant.now();
        Comment comment = new Comment(UUID.randomUUID(), gameId, null,
                identity.playerId(), body, now);
        commentRepo.save(comment);
        long seq = game.incrementAndGetSeq();
        gameRepo.save(game);
        eventPublisher.publishEvent(new GameDomainEvent.CommentPosted(this, gameId, seq,
                comment.getId(), identity.playerId(), author.getDisplayName(), body, now));
        return toDto(comment, author.getDisplayName());
    }

    @Transactional
    public void toggleLike(UUID gameId, UUID commentId, PlayerIdentity identity) {
        ScopeGuard.requirePlayerScope(identity, gameId);
        Comment comment = commentRepo.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found: " + commentId));
        Game game = requireGame(gameId);
        boolean liked = likeRepo.existsByIdCommentIdAndIdPlayerId(commentId, identity.playerId());
        if (liked) {
            likeRepo.deleteByIdCommentIdAndIdPlayerId(commentId, identity.playerId());
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
        } else {
            likeRepo.save(new CommentLike(commentId, identity.playerId(), gameId, Instant.now()));
            comment.setLikeCount(comment.getLikeCount() + 1);
        }
        commentRepo.save(comment);
        long seq = game.incrementAndGetSeq();
        gameRepo.save(game);
        eventPublisher.publishEvent(new GameDomainEvent.CommentLiked(this, gameId, seq,
                comment.getId(), comment.getLikeCount()));
    }

    @Transactional(readOnly = true)
    public List<CommentDto> getComments(UUID gameId, PlayerIdentity identity, int page) {
        ScopeGuard.requireScope(identity, gameId);
        requireGame(gameId);
        List<Comment> comments = commentRepo.findByGameIdOrderByCreatedAtDesc(
                gameId, PageRequest.of(page, PAGE_SIZE));
        return comments.stream().map(c -> {
            String name = playerRepo.findById(c.getAuthorPlayerId())
                    .map(Player::getDisplayName).orElse("?");
            return toDto(c, name);
        }).toList();
    }

    private CommentDto toDto(Comment c, String authorName) {
        return new CommentDto(c.getId(), c.getAuthorPlayerId(), authorName,
                c.getBody(), c.getCreatedAt(), c.getLikeCount());
    }

    private Game requireGame(UUID gameId) {
        return gameRepo.findById(gameId)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameId));
    }

    private Player requirePlayer(UUID playerId, UUID gameId) {
        return playerRepo.findByIdAndGameId(playerId, gameId)
                .orElseThrow(() -> new EntityNotFoundException("Player not found."));
    }
}

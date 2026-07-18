package com.karaogui.backend.comment;

import com.karaogui.backend.auth.PlayerIdentity;
import com.karaogui.backend.comment.dto.CommentDto;
import com.karaogui.backend.comment.dto.PostCommentRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games/{gameId}/comments")
class CommentController {

    private final CommentService commentService;

    CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CommentDto post(@PathVariable UUID gameId, @RequestBody PostCommentRequest req,
            PlayerIdentity identity) {
        return commentService.postComment(gameId, identity, req);
    }

    @PostMapping("/{commentId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void like(@PathVariable UUID gameId, @PathVariable UUID commentId, PlayerIdentity identity) {
        commentService.toggleLike(gameId, commentId, identity);
    }

    @GetMapping
    List<CommentDto> list(@PathVariable UUID gameId,
            @RequestParam(defaultValue = "0") int page, PlayerIdentity identity) {
        return commentService.getComments(gameId, identity, page);
    }
}

package com.karaogui.backend.error;

import com.karaogui.backend.auth.GameScopeException;
import jakarta.persistence.EntityNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    ResponseEntity<ApiError.Envelope> notFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("NOT_FOUND", ex.getMessage()).wrap());
    }

    @ExceptionHandler(GameScopeException.class)
    ResponseEntity<ApiError.Envelope> forbidden(GameScopeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(ex.getCode(), ex.getMessage()).wrap());
    }

    @ExceptionHandler(GameStateException.class)
    ResponseEntity<ApiError.Envelope> conflict(GameStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(ex.getCode(), ex.getMessage()).wrap());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError.Envelope> validation(MethodArgumentNotValidException ex) {
        var fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(java.util.stream.Collectors.toMap(
                        org.springframework.validation.FieldError::getField,
                        e -> (Object) (e.getDefaultMessage() != null ? e.getDefaultMessage() : "invalid"),
                        (a, b) -> a));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("VALIDATION_ERROR", "Request validation failed", fields).wrap());
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ApiError.Envelope> unauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(ex.getCode(), ex.getMessage()).wrap());
    }
}

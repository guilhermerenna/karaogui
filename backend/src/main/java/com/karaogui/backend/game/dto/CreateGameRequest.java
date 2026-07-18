package com.karaogui.backend.game.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateGameRequest(@NotNull @Valid HostInfo host) {

    public record HostInfo(
            @NotBlank @Size(max = 50) String displayName,
            String pictureUploadId) {}
}

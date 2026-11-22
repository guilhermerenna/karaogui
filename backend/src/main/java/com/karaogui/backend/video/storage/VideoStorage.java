package com.karaogui.backend.video.storage;

import java.util.Optional;
import org.springframework.core.io.Resource;

public interface VideoStorage {

    /**
     * Normalizes an incoming path or {@code file://} URL into a relative path within the configured
     * video root directory, rejecting empty paths or traversal attempts outside the root.
     *
     * @throws IllegalArgumentException if the input is blank, malformed, or escapes the video root
     */
    String normalizeToRelativePath(String inputPath);

    /**
     * Resolves a stored relative path into a readable {@link Resource}, returning empty if the path
     * escapes the configured video root or the file does not exist / cannot be read.
     */
    Optional<Resource> resolve(String storedPath);
}

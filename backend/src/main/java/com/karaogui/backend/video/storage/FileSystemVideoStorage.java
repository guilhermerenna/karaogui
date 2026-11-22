package com.karaogui.backend.video.storage;

import com.karaogui.backend.config.KaraoguiProperties;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class FileSystemVideoStorage implements VideoStorage {

    private final Path rootDir;

    public FileSystemVideoStorage(KaraoguiProperties props) {
        this.rootDir = props.videos().rootDir().toAbsolutePath().normalize();
    }

    @Override
    public String normalizeToRelativePath(String inputPath) {
        if (inputPath == null || inputPath.isBlank()) {
            throw new IllegalArgumentException("Video path must not be blank.");
        }
        String cleaned = stripFileScheme(inputPath.trim());
        Path resolved = resolveUnderRoot(cleaned);
        if (resolved == null) {
            throw new IllegalArgumentException("Video path must point to a file inside " + rootDir);
        }
        return rootDir.relativize(resolved).toString().replace('\\', '/');
    }

    @Override
    public Optional<Resource> resolve(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return Optional.empty();
        }
        String cleaned = stripFileScheme(storedPath.trim());
        Path resolved = resolveUnderRoot(cleaned);
        if (resolved == null || !Files.isRegularFile(resolved) || !Files.isReadable(resolved)) {
            return Optional.empty();
        }
        return Optional.of(new FileSystemResource(resolved));
    }

    private Path resolveUnderRoot(String rawPath) {
        try {
            Path candidate = Path.of(rawPath);
            Path resolved;
            if (candidate.isAbsolute()) {
                Path normalized = candidate.normalize();
                if (normalized.startsWith(rootDir)) {
                    resolved = normalized;
                } else {
                    // Treat leading "/" as relative to the configured video root
                    // (e.g. "/Radiohead-Creep.mp4" -> "<rootDir>/Radiohead-Creep.mp4").
                    String stripped = rawPath.replaceFirst("^/+", "");
                    if (stripped.isEmpty()) {
                        return null;
                    }
                    resolved = rootDir.resolve(stripped).normalize();
                }
            } else {
                resolved = rootDir.resolve(candidate).normalize();
            }
            if (!resolved.startsWith(rootDir) || resolved.equals(rootDir)) {
                return null;
            }
            return resolved;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private static String stripFileScheme(String raw) {
        if (!raw.startsWith("file://")) {
            return raw;
        }
        try {
            String decoded = URI.create(raw).getPath();
            if (decoded != null && !decoded.isBlank()) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // Fall through when raw file:// string contains unescaped spaces.
        }
        return raw.substring("file://".length());
    }
}

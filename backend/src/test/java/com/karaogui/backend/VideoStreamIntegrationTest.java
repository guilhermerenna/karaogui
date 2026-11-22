package com.karaogui.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.karaogui.backend.video.Video;
import com.karaogui.backend.video.VideoRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.FileSystemUtils;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class VideoStreamIntegrationTest {

    private static final Path VIDEO_ROOT;
    private static final byte[] SAMPLE_BYTES = new byte[4096];

    static {
        try {
            VIDEO_ROOT = Files.createTempDirectory("karaogui-video-stream-test-");
        } catch (IOException e) {
            throw new IllegalStateException("Could not create temp video directory", e);
        }
        for (int i = 0; i < SAMPLE_BYTES.length; i++) {
            SAMPLE_BYTES[i] = (byte) (i % 251);
        }
    }

    @DynamicPropertySource
    static void videoProps(DynamicPropertyRegistry registry) {
        registry.add("karaogui.videos.root-dir", () -> VIDEO_ROOT.toAbsolutePath().toString());
        registry.add("karaogui.videos.stream-chunk-size", () -> "1KB");
    }

    @BeforeAll
    static void seedVideoFile() throws IOException {
        Path nested = VIDEO_ROOT.resolve("rock");
        Files.createDirectories(nested);
        Files.write(nested.resolve("sample.mp4"), SAMPLE_BYTES);
    }

    @AfterAll
    static void cleanup() throws IOException {
        FileSystemUtils.deleteRecursively(VIDEO_ROOT);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    VideoRepository videoRepo;

    @Test
    void stream_returnsRequestedByteRangesWithoutAuth() throws Exception {
        UUID videoId = UUID.randomUUID();
        videoRepo.save(new Video(videoId, null, null, "rock/sample.mp4",
                "Sample Track", null, "Sample", "Artist", 180L, Instant.now(), null));

        // Initial byte range request (0-511)
        MvcResult firstChunk = mvc.perform(get("/api/videos/" + videoId + "/stream")
                .header(HttpHeaders.RANGE, "bytes=0-511"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/mp4"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-511/4096"))
                .andReturn();
        assertThat(firstChunk.getResponse().getContentAsByteArray())
                .isEqualTo(Arrays.copyOfRange(SAMPLE_BYTES, 0, 512));

        // Mid-file range request (1024-2047)
        MvcResult midChunk = mvc.perform(get("/api/videos/" + videoId + "/stream")
                .header(HttpHeaders.RANGE, "bytes=1024-2047"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 1024-2047/4096"))
                .andReturn();
        assertThat(midChunk.getResponse().getContentAsByteArray())
                .isEqualTo(Arrays.copyOfRange(SAMPLE_BYTES, 1024, 2048));

        // No Range header returns the first configured chunk (1KB = 1024 bytes)
        MvcResult defaultChunk = mvc.perform(get("/api/videos/" + videoId + "/stream"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-1023/4096"))
                .andReturn();
        assertThat(defaultChunk.getResponse().getContentAsByteArray())
                .isEqualTo(Arrays.copyOfRange(SAMPLE_BYTES, 0, 1024));

        // Out-of-bounds range returns 416
        mvc.perform(get("/api/videos/" + videoId + "/stream")
                .header(HttpHeaders.RANGE, "bytes=9999-10000"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */4096"));
    }

    @Test
    void stream_returns404ForMissingOrUnstreamableVideos() throws Exception {
        // Unknown video ID
        mvc.perform(get("/api/videos/" + UUID.randomUUID() + "/stream"))
                .andExpect(status().isNotFound());

        // YouTube-only video (no local_path)
        UUID ytOnlyId = UUID.randomUUID();
        videoRepo.save(new Video(ytOnlyId, "ytStreamTest", "https://youtu.be/ytStreamTest",
                null, "YT Only", null, null, null, 100L, Instant.now(), null));
        mvc.perform(get("/api/videos/" + ytOnlyId + "/stream"))
                .andExpect(status().isNotFound());

        // Missing file on disk
        UUID missingFileId = UUID.randomUUID();
        videoRepo.save(new Video(missingFileId, null, null, "does-not-exist.mp4",
                "Missing", null, null, null, 100L, Instant.now(), null));
        mvc.perform(get("/api/videos/" + missingFileId + "/stream"))
                .andExpect(status().isNotFound());

        // Direct DB path traversal attempt
        UUID traversalId = UUID.randomUUID();
        videoRepo.save(new Video(traversalId, null, null, "../../etc/passwd",
                "Traversal", null, null, null, 100L, Instant.now(), null));
        mvc.perform(get("/api/videos/" + traversalId + "/stream"))
                .andExpect(status().isNotFound());
    }
}

package com.karaogui.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.karaogui.backend.video.Video;
import com.karaogui.backend.video.VideoRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "karaogui.features.youtube=true")
@AutoConfigureMockMvc
class YoutubeFeatureEnabledIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    VideoRepository videoRepo;

    private record Game(String gameId, String joinCode, String hostToken) {}

    private Game createGame() throws Exception {
        MvcResult tvResult = mvc.perform(post("/api/tv/register"))
                .andExpect(status().isOk())
                .andReturn();
        String tvCode = mapper.readTree(tvResult.getResponse().getContentAsString()).get("joinCode").asText();

        MvcResult createResult = mvc.perform(post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"host": {"displayName": "Host"}, "tvCode": "%s"}
                        """.formatted(tvCode)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = mapper.readTree(createResult.getResponse().getContentAsString());
        return new Game(body.get("gameId").asText(), body.get("joinCode").asText(),
                body.get("sessionToken").asText());
    }

    private void join(String joinCode, String name) throws Exception {
        mvc.perform(post("/api/games/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"joinCode": "%s", "player": {"displayName": "%s"}}
                        """.formatted(joinCode, name)))
                .andExpect(status().isCreated());
    }

    @Test
    void importIsIdempotent_dedupesByYoutubeId() throws Exception {
        Game game = createGame();

        MvcResult first = mvc.perform(post("/api/videos")
                .header("Authorization", "Bearer " + game.hostToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"youtubeUrl": "https://youtu.be/dedupe1", "songTitle": "Bohemian Rhapsody", "artist": "Queen"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String firstId = mapper.readTree(first.getResponse().getContentAsString()).get("videoId").asText();

        // Same URL again → same video row (dedupe by youtube_id)
        MvcResult second = mvc.perform(post("/api/videos")
                .header("Authorization", "Bearer " + game.hostToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"youtubeUrl": "https://youtu.be/dedupe1"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String secondId = mapper.readTree(second.getResponse().getContentAsString()).get("videoId").asText();

        assertThat(secondId).isEqualTo(firstId);
    }

    @Test
    void registerLocalVideo_attachesLocalPathToExistingYoutubeVideo() throws Exception {
        Game game = createGame();

        MvcResult imported = mvc.perform(post("/api/videos")
                .header("Authorization", "Bearer " + game.hostToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"youtubeUrl": "https://youtu.be/hybrid123", "songTitle": "Hybrid Song"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String videoId = mapper.readTree(imported.getResponse().getContentAsString()).get("videoId").asText();

        MvcResult attached = mvc.perform(post("/api/videos/local")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"videoId": "%s", "videoUrl": "hybrid-song.mp4"}
                        """.formatted(videoId)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode attachedBody = mapper.readTree(attached.getResponse().getContentAsString());
        assertThat(attachedBody.get("videoId").asText()).isEqualTo(videoId);
        assertThat(attachedBody.get("hasLocalFile").asBoolean()).isTrue();
        assertThat(attachedBody.get("youtubeUrl").asText()).isEqualTo("https://youtu.be/hybrid123");
    }

    @Test
    void queueByYoutubeOnlyVideoId_succeedsWhenEnabled() throws Exception {
        Game game = createGame();
        join(game.joinCode(), "Ana");
        join(game.joinCode(), "Bob");

        UUID videoId = UUID.randomUUID();
        videoRepo.save(new Video(videoId, "seededyt", "https://youtu.be/seededyt",
                "Seeded YT Video", "https://img/thumb.jpg", "Seed Song", "Seed Artist",
                123L, Instant.now(), null));

        mvc.perform(post("/api/games/" + game.gameId() + "/start")
                .header("Authorization", "Bearer " + game.hostToken()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/games/" + game.gameId() + "/performances")
                .header("Authorization", "Bearer " + game.hostToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type": "KARAOKE", "videoId": "%s"}
                        """.formatted(videoId)))
                .andExpect(status().isCreated());

        MvcResult snap = mvc.perform(get("/api/games/" + game.gameId())
                .header("Authorization", "Bearer " + game.hostToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode current = mapper.readTree(snap.getResponse().getContentAsString()).get("currentPerformance");
        assertThat(current).isNotNull();
        assertThat(current.get("durationSeconds").asLong()).isEqualTo(123L);
        // YouTube-only video has no local file, so videoId in performance payloads is null.
        assertThat(current.get("videoId").isNull()).isTrue();
    }
}

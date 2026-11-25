package com.karaogui.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class VideoLibraryIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    VideoRepository videoRepo;

    private record Game(String gameId, String joinCode, String hostToken) {}

    private Game createGameWithPlayers() throws Exception {
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

    private String join(String joinCode, String name) throws Exception {
        MvcResult r = mvc.perform(post("/api/games/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"joinCode": "%s", "player": {"displayName": "%s"}}
                        """.formatted(joinCode, name)))
                .andExpect(status().isCreated())
                .andReturn();
        return mapper.readTree(r.getResponse().getContentAsString()).get("sessionToken").asText();
    }

    @Test
    void youtubeImportEndpoint_isDisabledByDefault() throws Exception {
        Game game = createGameWithPlayers();

        mvc.perform(post("/api/videos")
                .header("Authorization", "Bearer " + game.hostToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"youtubeUrl": "https://youtu.be/dedupe1", "songTitle": "Bohemian Rhapsody", "artist": "Queen"}
                        """))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void queuePerformance_rejectsYoutubeUrlAndYoutubeOnlyVideoWhenDisabled() throws Exception {
        Game game = createGameWithPlayers();
        join(game.joinCode(), "Ana");
        join(game.joinCode(), "Bob");

        mvc.perform(post("/api/games/" + game.gameId() + "/start")
                .header("Authorization", "Bearer " + game.hostToken()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/games/" + game.gameId() + "/performances")
                .header("Authorization", "Bearer " + game.hostToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type": "KARAOKE", "youtubeUrl": "https://youtu.be/blocked"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("YOUTUBE_DISABLED"));

        UUID ytOnlyId = UUID.randomUUID();
        videoRepo.save(new Video(ytOnlyId, "blockedyt", "https://youtu.be/blockedyt",
                "Blocked YT", null, "Blocked Song", "Blocked Artist",
                180L, Instant.now(), null));

        mvc.perform(post("/api/games/" + game.gameId() + "/performances")
                .header("Authorization", "Bearer " + game.hostToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type": "KARAOKE", "videoId": "%s"}
                        """.formatted(ytOnlyId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("YOUTUBE_DISABLED"));
    }

    @Test
    void search_findsLocalByArtist_andHidesYoutubeOnlyVideos() throws Exception {
        Game game = createGameWithPlayers();
        mvc.perform(post("/api/videos/local")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"videoUrl": "/videos/oasis/wonderwall.mp4", "songTitle": "Wonderwall", "artist": "Oasis"}
                        """))
                .andExpect(status().isCreated());

        videoRepo.save(new Video(UUID.randomUUID(), "hiddenyt", "https://youtu.be/hiddenyt",
                "Hidden YT", null, "Hidden Song", "HiddenYT",
                200L, Instant.now(), null));

        MvcResult res = mvc.perform(get("/api/videos?q=oasis")
                .header("Authorization", "Bearer " + game.hostToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = mapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("content").size()).isGreaterThanOrEqualTo(1);
        boolean found = false;
        for (JsonNode v : body.get("content")) {
            if ("Oasis".equals(v.path("artist").asText(null))) found = true;
        }
        assertThat(found).isTrue();

        MvcResult hiddenRes = mvc.perform(get("/api/videos?q=HiddenYT")
                .header("Authorization", "Bearer " + game.hostToken()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(mapper.readTree(hiddenRes.getResponse().getContentAsString()).get("content")).isEmpty();
    }

    @Test
    void deleteVideoEndpoint_isDisabledByDefault() throws Exception {
        Game game = createGameWithPlayers();

        MvcResult imported = mvc.perform(post("/api/videos/local")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"videoUrl": "/videos/delete-me.mp4", "songTitle": "Delete Me"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String videoId = mapper.readTree(imported.getResponse().getContentAsString()).get("videoId").asText();

        mvc.perform(delete("/api/videos/" + videoId)
                .header("Authorization", "Bearer " + game.hostToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void queueByVideoId_copiesStoredDuration() throws Exception {
        Game game = createGameWithPlayers();
        join(game.joinCode(), "Ana");
        join(game.joinCode(), "Bob");

        // Seed a local library video with a known duration.
        UUID videoId = UUID.randomUUID();
        videoRepo.save(new Video(videoId, null, null, "seeded.mp4",
                "Seeded Video", null, "Seed Song", "Seed Artist",
                123L, Instant.now(), null));

        mvc.perform(post("/api/games/" + game.gameId() + "/start")
                .header("Authorization", "Bearer " + game.hostToken()))
                .andExpect(status().isOk());

        MvcResult queued = mvc.perform(post("/api/games/" + game.gameId() + "/performances")
                .header("Authorization", "Bearer " + game.hostToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type": "KARAOKE", "videoId": "%s"}
                        """.formatted(videoId)))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(mapper.readTree(queued.getResponse().getContentAsString())
                .get("performanceId").asText()).isNotBlank();

        // The queued/announced performance surfaces via the snapshot with the stored duration.
        MvcResult snap = mvc.perform(get("/api/games/" + game.gameId())
                .header("Authorization", "Bearer " + game.hostToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode current = mapper.readTree(snap.getResponse().getContentAsString()).get("currentPerformance");
        assertThat(current).isNotNull();
        assertThat(current.get("durationSeconds").asLong()).isEqualTo(123L);
        assertThat(current.get("videoId").asText()).isEqualTo(videoId.toString());
    }

    @Test
    void registerLocalVideo_createsLocalOnlyAndSurfacesVideoIdOnPerformance() throws Exception {
        Game game = createGameWithPlayers();
        join(game.joinCode(), "Ana");
        join(game.joinCode(), "Bob");

        // Register a local-only video using a full container path (no auth required).
        MvcResult reg = mvc.perform(post("/api/videos/local")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"videoUrl": "/videos/queen/bohemian.mp4", "songTitle": "Bohemian Rhapsody", "artist": "Queen", "durationSeconds": 355}
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode regBody = mapper.readTree(reg.getResponse().getContentAsString());
        String videoId = regBody.get("videoId").asText();
        assertThat(regBody.get("hasLocalFile").asBoolean()).isTrue();
        assertThat(regBody.get("youtubeUrl").isNull()).isTrue();
        assertThat(regBody.get("videoName").asText()).isEqualTo("bohemian");

        // Idempotent when re-registered via file:// URL pointing to the same file.
        MvcResult regAgain = mvc.perform(post("/api/videos/local")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"videoUrl": "file:///videos/queen/bohemian.mp4"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(mapper.readTree(regAgain.getResponse().getContentAsString()).get("videoId").asText())
                .isEqualTo(videoId);

        // Leading-slash path relative to video root also resolves to the same file.
        MvcResult regLeadingSlash = mvc.perform(post("/api/videos/local")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"videoUrl": "/queen/bohemian.mp4"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(mapper.readTree(regLeadingSlash.getResponse().getContentAsString()).get("videoId").asText())
                .isEqualTo(videoId);

        // Path traversal outside /videos is rejected (both relative and leading-slash).
        mvc.perform(post("/api/videos/local")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"videoUrl": "../../etc/passwd"}
                        """))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/videos/local")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"videoUrl": "/../../etc/passwd"}
                        """))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/games/" + game.gameId() + "/start")
                .header("Authorization", "Bearer " + game.hostToken()))
                .andExpect(status().isOk());

        // Queueing the local video surfaces videoId on both PerformanceDetailDto and CurrentPerformanceDto.
        MvcResult queued = mvc.perform(post("/api/games/" + game.gameId() + "/performances")
                .header("Authorization", "Bearer " + game.hostToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type": "KARAOKE", "videoId": "%s"}
                        """.formatted(videoId)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode detail = mapper.readTree(queued.getResponse().getContentAsString());
        assertThat(detail.get("videoId").asText()).isEqualTo(videoId);
        assertThat(detail.get("youtubeUrl").isNull()).isTrue();

        MvcResult snap = mvc.perform(get("/api/games/" + game.gameId())
                .header("Authorization", "Bearer " + game.hostToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode current = mapper.readTree(snap.getResponse().getContentAsString()).get("currentPerformance");
        assertThat(current.get("videoId").asText()).isEqualTo(videoId);
        assertThat(current.get("durationSeconds").asLong()).isEqualTo(355L);
    }

    @Test
    void search_clampsHugeAndNegativePage() throws Exception {
        Game game = createGameWithPlayers();

        mvc.perform(get("/api/videos?page=99999999")
                .header("Authorization", "Bearer " + game.hostToken()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/videos?page=-5")
                .header("Authorization", "Bearer " + game.hostToken()))
                .andExpect(status().isOk());
    }
}

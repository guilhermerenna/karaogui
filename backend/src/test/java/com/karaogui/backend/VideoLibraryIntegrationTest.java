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
        MvcResult createResult = mvc.perform(post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"host": {"displayName": "Host"}}
                        """))
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
    void importIsIdempotent_dedupesByYoutubeId() throws Exception {
        Game game = createGameWithPlayers();

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
    void search_findsByArtist() throws Exception {
        Game game = createGameWithPlayers();
        mvc.perform(post("/api/videos")
                .header("Authorization", "Bearer " + game.hostToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"youtubeUrl": "https://youtu.be/searchme", "songTitle": "Wonderwall", "artist": "Oasis"}
                        """))
                .andExpect(status().isCreated());

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
    }

    @Test
    void delete_hostAllowed_nonHostForbidden() throws Exception {
        Game game = createGameWithPlayers();
        String guestToken = join(game.joinCode(), "Guest");

        MvcResult imported = mvc.perform(post("/api/videos")
                .header("Authorization", "Bearer " + game.hostToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"youtubeUrl": "https://youtu.be/deleteme", "songTitle": "Delete Me"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String videoId = mapper.readTree(imported.getResponse().getContentAsString()).get("videoId").asText();

        // Non-host cannot delete
        mvc.perform(delete("/api/videos/" + videoId)
                .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isForbidden());

        // Host can delete
        mvc.perform(delete("/api/videos/" + videoId)
                .header("Authorization", "Bearer " + game.hostToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void queueByVideoId_copiesStoredDuration() throws Exception {
        Game game = createGameWithPlayers();
        join(game.joinCode(), "Ana");
        join(game.joinCode(), "Bob");

        // Seed a library video with a known duration (no API key in tests, so insert directly).
        UUID videoId = UUID.randomUUID();
        videoRepo.save(new Video(videoId, "seededid", "https://youtu.be/seededid",
                "Seeded Video", "https://img/thumb.jpg", "Seed Song", "Seed Artist",
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
    }
}

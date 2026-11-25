package com.karaogui.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        properties = "karaogui.features.video-delete=true")
@AutoConfigureMockMvc
class VideoDeleteFeatureEnabledIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

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
    void delete_hostAllowed_nonHostForbidden() throws Exception {
        Game game = createGame();
        String guestToken = join(game.joinCode(), "Guest");

        MvcResult imported = mvc.perform(post("/api/videos/local")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"videoUrl": "/videos/delete-enabled.mp4", "songTitle": "Delete Me"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String videoId = mapper.readTree(imported.getResponse().getContentAsString()).get("videoId").asText();

        mvc.perform(delete("/api/videos/" + videoId)
                .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/videos/" + videoId)
                .header("Authorization", "Bearer " + game.hostToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_playedVideoSucceeds() throws Exception {
        Game game = createGame();
        join(game.joinCode(), "Ana");
        join(game.joinCode(), "Bob");

        MvcResult reg = mvc.perform(post("/api/videos/local")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"videoUrl": "/videos/played-delete.mp4", "songTitle": "Played Song", "durationSeconds": 200}
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String videoId = mapper.readTree(reg.getResponse().getContentAsString()).get("videoId").asText();

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

        mvc.perform(delete("/api/videos/" + videoId)
                .header("Authorization", "Bearer " + game.hostToken()))
                .andExpect(status().isNoContent());
    }
}

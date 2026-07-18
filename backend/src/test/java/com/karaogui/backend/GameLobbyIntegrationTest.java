package com.karaogui.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class GameLobbyIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void createJoinStart_happyPath() throws Exception {
        // 1. Create game (host)
        MvcResult createResult = mvc.perform(post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"host": {"displayName": "Sam"}}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameId").exists())
                .andExpect(jsonPath("$.joinCode").exists())
                .andExpect(jsonPath("$.sessionToken").exists())
                .andExpect(jsonPath("$.displayToken").exists())
                .andExpect(jsonPath("$.state").value("CREATED"))
                .andReturn();

        JsonNode createBody = mapper.readTree(createResult.getResponse().getContentAsString());
        String gameId = createBody.get("gameId").asText();
        String hostToken = createBody.get("sessionToken").asText();
        String joinCode = createBody.get("joinCode").asText();
        String displayToken = createBody.get("displayToken").asText();

        // 2. Join game (second player)
        MvcResult joinResult = mvc.perform(post("/api/games/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"joinCode": "%s", "player": {"displayName": "Ana"}}
                        """.formatted(joinCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionToken").exists())
                .andExpect(jsonPath("$.gameId").value(gameId))
                .andReturn();

        String guestToken = mapper.readTree(joinResult.getResponse().getContentAsString())
                .get("sessionToken").asText();

        // 3. Snapshot visible to both players
        mvc.perform(get("/api/games/" + gameId)
                .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(2))
                .andExpect(jsonPath("$.state").value("CREATED"));

        // 4. Display token can also read snapshot
        mvc.perform(get("/api/games/" + gameId)
                .header("Authorization", "Bearer " + displayToken))
                .andExpect(status().isOk());

        // 5. Non-host cannot start
        mvc.perform(post("/api/games/" + gameId + "/start")
                .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isForbidden());

        // 6. Host starts game
        mvc.perform(post("/api/games/" + gameId + "/start")
                .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"));

        // 7. Starting again is a conflict
        mvc.perform(post("/api/games/" + gameId + "/start")
                .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isConflict());

        // 8. Players list
        mvc.perform(get("/api/games/" + gameId + "/players")
                .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // 9. My player
        mvc.perform(get("/api/games/" + gameId + "/players/me")
                .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isHost").value(true));

        // 10. Ranking
        mvc.perform(get("/api/games/" + gameId + "/ranking")
                .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPlayers").value(2));
    }

    @Test
    void joinWithBadCode_returns404() throws Exception {
        mvc.perform(post("/api/games/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"joinCode": "ZZZ999", "player": {"displayName": "Ana"}}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void tokenForWrongGame_returns403() throws Exception {
        MvcResult r1 = mvc.perform(post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"host": {"displayName": "Alice"}}
                        """))
                .andExpect(status().isCreated()).andReturn();
        MvcResult r2 = mvc.perform(post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"host": {"displayName": "Bob"}}
                        """))
                .andExpect(status().isCreated()).andReturn();

        String gameId2 = mapper.readTree(r2.getResponse().getContentAsString()).get("gameId").asText();
        String tokenGame1 = mapper.readTree(r1.getResponse().getContentAsString()).get("sessionToken").asText();

        mvc.perform(get("/api/games/" + gameId2)
                .header("Authorization", "Bearer " + tokenGame1))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingToken_returns401() throws Exception {
        mvc.perform(get("/api/games/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validation_blankDisplayName_returns400() throws Exception {
        mvc.perform(post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"host": {"displayName": ""}}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void joinWithSpacedCode_normalizes() throws Exception {
        MvcResult createResult = mvc.perform(post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"host": {"displayName": "Host"}}
                        """))
                .andExpect(status().isCreated()).andReturn();

        JsonNode body = mapper.readTree(createResult.getResponse().getContentAsString());
        String displayCode = body.get("joinCodeDisplay").asText();

        mvc.perform(post("/api/games/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"joinCode": "%s", "player": {"displayName": "Joiner"}}
                        """.formatted(displayCode)))
                .andExpect(status().isCreated());
    }
}

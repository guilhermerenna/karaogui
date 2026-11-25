package com.karaogui.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.karaogui.backend.tv.PendingTvSession;
import com.karaogui.backend.tv.PendingTvSessionRepository;
import com.karaogui.backend.tv.TvService;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
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
@SpringBootTest
@AutoConfigureMockMvc
class TvSecurityIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PendingTvSessionRepository pendingTvRepo;
    @Autowired TvService tvService;

    @AfterEach
    void cleanup() {
        pendingTvRepo.deleteAll();
    }

    @Test
    void registerTv_prunesExpiredSessions_andEnforcesMaxPendingCap() throws Exception {
        pendingTvRepo.save(new PendingTvSession(
                "EXP001", "expired-hash-1", Instant.now().minus(Duration.ofMinutes(31))));

        mvc.perform(post("/api/tv/register"))
                .andExpect(status().isOk());

        assertThat(pendingTvRepo.findById("EXP001")).isEmpty();

        long existing = pendingTvRepo.count();
        for (long i = existing; i < TvService.MAX_PENDING_SESSIONS; i++) {
            String code = String.format("CAP%03d", i);
            pendingTvRepo.save(new PendingTvSession(code, "cap-hash-" + i, Instant.now()));
        }

        mvc.perform(post("/api/tv/register"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TOO_MANY_PENDING_TV_SESSIONS"));
    }

    @Test
    void createGame_rejectsExpiredTvCode_andPurgeDeletesIt() throws Exception {
        pendingTvRepo.save(new PendingTvSession(
                "OLD123", "expired-hash-2", Instant.now().minus(Duration.ofMinutes(31))));

        mvc.perform(post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"host": {"displayName": "Host"}, "tvCode": "OLD123"}
                        """))
                .andExpect(status().isNotFound());

        tvService.purgeExpiredSessions();
        assertThat(pendingTvRepo.findById("OLD123")).isEmpty();
    }

    @Test
    void getSnapshotByCode_preventsJoinCodeEnumeration() throws Exception {
        MvcResult tv1Res = mvc.perform(post("/api/tv/register"))
                .andExpect(status().isOk()).andReturn();
        JsonNode tv1 = mapper.readTree(tv1Res.getResponse().getContentAsString());
        String code1 = tv1.get("joinCode").asText();
        String displayToken1 = tv1.get("displayToken").asText();

        mvc.perform(post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"host": {"displayName": "Host1"}, "tvCode": "%s"}
                        """.formatted(code1)))
                .andExpect(status().isCreated());

        MvcResult tv2Res = mvc.perform(post("/api/tv/register"))
                .andExpect(status().isOk()).andReturn();
        String code2 = mapper.readTree(tv2Res.getResponse().getContentAsString()).get("joinCode").asText();

        mvc.perform(post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"host": {"displayName": "Host2"}, "tvCode": "%s"}
                        """.formatted(code2)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/games/by-code/" + code1))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/games/by-code/" + code1)
                .header("Authorization", "Bearer " + displayToken1))
                .andExpect(status().isOk());

        mvc.perform(get("/api/games/by-code/" + code2)
                .header("Authorization", "Bearer " + displayToken1))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/games/by-code/ZZZ999")
                .header("Authorization", "Bearer " + displayToken1))
                .andExpect(status().isForbidden());
    }
}

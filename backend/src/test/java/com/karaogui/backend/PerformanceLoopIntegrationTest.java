package com.karaogui.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class PerformanceLoopIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    WebSocketStompClient stompClient;

    @Test
    void karaokeLoop_announceConfirmEvaluateLock() throws Exception {
        // 1. Create game (host)
        MvcResult createResult = mvc.perform(post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"host": {"displayName": "Host"}}
                        """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createBody = mapper.readTree(createResult.getResponse().getContentAsString());
        String gameId = createBody.get("gameId").asText();
        String joinCode = createBody.get("joinCode").asText();
        String displayToken = createBody.get("displayToken").asText();
        String hostToken = createBody.get("sessionToken").asText();
        String hostPlayerId = createBody.get("you").get("playerId").asText();

        // 2. Two players join
        MvcResult joinAna = mvc.perform(post("/api/games/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"joinCode": "%s", "player": {"displayName": "Ana"}}
                        """.formatted(joinCode)))
                .andExpect(status().isCreated())
                .andReturn();
        String anaToken = mapper.readTree(joinAna.getResponse().getContentAsString()).get("sessionToken").asText();
        String anaId = mapper.readTree(joinAna.getResponse().getContentAsString()).get("you").get("playerId").asText();

        MvcResult joinBob = mvc.perform(post("/api/games/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"joinCode": "%s", "player": {"displayName": "Bob"}}
                        """.formatted(joinCode)))
                .andExpect(status().isCreated())
                .andReturn();
        String bobToken = mapper.readTree(joinBob.getResponse().getContentAsString()).get("sessionToken").asText();
        String bobId = mapper.readTree(joinBob.getResponse().getContentAsString()).get("you").get("playerId").asText();

        // Cara joins as audience so a 2-performer karaoke keeps 1 judge (Host) + 1 audience (Cara)
        MvcResult joinCara = mvc.perform(post("/api/games/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"joinCode": "%s", "player": {"displayName": "Cara"}}
                        """.formatted(joinCode)))
                .andExpect(status().isCreated())
                .andReturn();
        String caraToken = mapper.readTree(joinCara.getResponse().getContentAsString()).get("sessionToken").asText();
        String caraId = mapper.readTree(joinCara.getResponse().getContentAsString()).get("you").get("playerId").asText();

        // 3. Host starts game
        mvc.perform(post("/api/games/" + gameId + "/start")
                .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());

        // 4. TV connects and subscribes to /performers
        BlockingQueue<JsonNode> performers = new LinkedBlockingQueue<>();
        String wsUrl = "ws://localhost:" + port + "/ws";

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + displayToken);

        StompSession tvSession = stompClient
                .connectAsync(wsUrl, (org.springframework.web.socket.WebSocketHttpHeaders) null,
                        connectHeaders, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        tvSession.subscribe("/topic/games/" + gameId + "/performers", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return String.class; }
            @Override public void handleFrame(StompHeaders h, Object p) {
                try { performers.add(mapper.readTree((String) p)); } catch (Exception e) { throw new RuntimeException(e); }
            }
        });

        BlockingQueue<JsonNode> rankingEvents = new LinkedBlockingQueue<>();
        tvSession.subscribe("/topic/games/" + gameId + "/ranking", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return String.class; }
            @Override public void handleFrame(StompHeaders h, Object p) {
                try { rankingEvents.add(mapper.readTree((String) p)); } catch (Exception e) { throw new RuntimeException(e); }
            }
        });

        // Small delay to ensure subscription is active
        Thread.sleep(200);

        // 5. Host queues Karaoke with Ana and Bob as performers
        MvcResult queueResult = mvc.perform(post("/api/games/" + gameId + "/performances")
                .header("Authorization", "Bearer " + hostToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type": "KARAOKE", "youtubeUrl": "https://youtu.be/test", "performerPlayerIds": ["%s", "%s"]}
                        """.formatted(anaId, bobId)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode queueBody = mapper.readTree(queueResult.getResponse().getContentAsString());
        String performanceId = queueBody.get("performanceId").asText();
        assertThat(performanceId).isNotBlank();

        // Exactly one judge is assigned (Host or Cara); resolve its token for the eval step.
        JsonNode judges = queueBody.get("judges");
        assertThat(judges.size()).isEqualTo(1);
        String judgeId = judges.get(0).get("judgePlayerId").asText();
        String judgeToken = judgeId.equals(caraId) ? caraToken : hostToken;

        // 6. TV receives PERFORMANCE_ANNOUNCED
        JsonNode announced = performers.poll(5, TimeUnit.SECONDS);
        assertThat(announced).isNotNull();
        assertThat(announced.get("type").asText()).isEqualTo("PERFORMANCE_ANNOUNCED");
        assertThat(announced.get("data").get("performanceId").asText()).isEqualTo(performanceId);
        assertThat(announced.get("data").get("confirmDeadlineAt")).isNotNull();

        // 7. Ana confirms her slot
        mvc.perform(post("/api/games/" + gameId + "/performances/" + performanceId + "/confirm")
                .header("Authorization", "Bearer " + anaToken))
                .andExpect(status().isNoContent());

        JsonNode slotChanged1 = performers.poll(5, TimeUnit.SECONDS);
        assertThat(slotChanged1).isNotNull();
        assertThat(slotChanged1.get("type").asText()).isEqualTo("SLOT_STATE_CHANGED");
        assertThat(slotChanged1.get("data").get("slotState").asText()).isEqualTo("CONFIRMED");

        // 8. Bob confirms → all slots resolved → PERFORMANCE_STARTED
        mvc.perform(post("/api/games/" + gameId + "/performances/" + performanceId + "/confirm")
                .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isNoContent());

        // Could get another SLOT_STATE_CHANGED then PERFORMANCE_STARTED
        JsonNode next = performers.poll(5, TimeUnit.SECONDS);
        assertThat(next).isNotNull();

        // Drain until PERFORMANCE_STARTED
        JsonNode startedEvent = null;
        if ("PERFORMANCE_STARTED".equals(next.get("type").asText())) {
            startedEvent = next;
        } else {
            // Was SLOT_STATE_CHANGED, next should be PERFORMANCE_STARTED
            startedEvent = performers.poll(5, TimeUnit.SECONDS);
        }
        assertThat(startedEvent).isNotNull();
        assertThat(startedEvent.get("type").asText()).isEqualTo("PERFORMANCE_STARTED");

        // 9. The assigned judge submits evaluation
        mvc.perform(post("/api/games/" + gameId + "/performances/" + performanceId + "/evaluate")
                .header("Authorization", "Bearer " + judgeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "baseline": [
                            {"criterion": "PITCH", "score": 8},
                            {"criterion": "ENERGY", "score": 7},
                            {"criterion": "STAGE_PRESENCE", "score": 9}
                          ],
                          "perPerformer": {}
                        }
                        """))
                .andExpect(status().isNoContent());

        // 10. Last judge submitted → PERFORMANCE_LOCKED
        JsonNode lockedEvent = performers.poll(5, TimeUnit.SECONDS);
        assertThat(lockedEvent).isNotNull();
        assertThat(lockedEvent.get("type").asText()).isEqualTo("PERFORMANCE_LOCKED");
        assertThat(lockedEvent.get("data").get("scores")).isNotNull();
        assertThat(lockedEvent.get("data").get("scores").size()).isEqualTo(2); // Ana and Bob

        // 11. RANKING_UPDATED fires (may come alongside PERFORMANCE_LOCKED)
        JsonNode rankingEvent = rankingEvents.poll(5, TimeUnit.SECONDS);
        assertThat(rankingEvent).isNotNull();
        assertThat(rankingEvent.get("type").asText()).isEqualTo("RANKING_UPDATED");

        // 12. Verify scores > 0 for performers (avg of PITCH=8,ENERGY=7,STAGE_PRESENCE=9 = 8.0; points = round(8*10) = 80)
        JsonNode scores = lockedEvent.get("data").get("scores");
        for (JsonNode score : scores) {
            assertThat(score.get("points").asInt()).isGreaterThan(0);
        }

        tvSession.disconnect();
    }
}

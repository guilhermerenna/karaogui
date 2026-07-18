package com.karaogui.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
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
class GameRealtimeIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    WebSocketStompClient stompClient;

    @Test
    void tvReceivesPlayerJoinedEvent() throws Exception {
        // 1. Create game
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

        // 2. TV connects with display token
        BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
        String wsUrl = "ws://localhost:" + port + "/ws";

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + displayToken);

        StompSession tvSession = stompClient
                .connectAsync(wsUrl, (org.springframework.web.socket.WebSocketHttpHeaders) null,
                        connectHeaders, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        tvSession.subscribe("/topic/games/" + gameId + "/players", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                try {
                    received.add(mapper.readTree((String) payload));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        // 3. Second player joins via REST
        mvc.perform(post("/api/games/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"joinCode": "%s", "player": {"displayName": "Ana"}}
                        """.formatted(joinCode)))
                .andExpect(status().isCreated());

        // 4. Assert TV receives PLAYER_JOINED with seq=1
        JsonNode event = received.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.get("type").asText()).isEqualTo("PLAYER_JOINED");
        assertThat(event.get("seq").asLong()).isEqualTo(1L);
        assertThat(event.get("data").get("displayName").asText()).isEqualTo("Ana");

        // 5. Third player joins → seq=2
        mvc.perform(post("/api/games/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"joinCode": "%s", "player": {"displayName": "Bob"}}
                        """.formatted(joinCode)))
                .andExpect(status().isCreated());

        JsonNode event2 = received.poll(5, TimeUnit.SECONDS);
        assertThat(event2).isNotNull();
        assertThat(event2.get("seq").asLong()).isEqualTo(2L);
        assertThat(event2.get("data").get("displayName").asText()).isEqualTo("Bob");

        // 6. Host starts game → GAME_STARTED on /state and RANKING_UPDATED on /ranking
        BlockingQueue<JsonNode> stateEvents = new LinkedBlockingQueue<>();
        BlockingQueue<JsonNode> rankingEvents = new LinkedBlockingQueue<>();

        tvSession.subscribe("/topic/games/" + gameId + "/state", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return String.class; }
            @Override public void handleFrame(StompHeaders h, Object p) {
                try { stateEvents.add(mapper.readTree((String) p)); } catch (Exception e) { throw new RuntimeException(e); }
            }
        });

        tvSession.subscribe("/topic/games/" + gameId + "/ranking", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return String.class; }
            @Override public void handleFrame(StompHeaders h, Object p) {
                try { rankingEvents.add(mapper.readTree((String) p)); } catch (Exception e) { throw new RuntimeException(e); }
            }
        });

        mvc.perform(post("/api/games/" + gameId + "/start")
                .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());

        JsonNode stateEvent = stateEvents.poll(5, TimeUnit.SECONDS);
        assertThat(stateEvent).isNotNull();
        assertThat(stateEvent.get("type").asText()).isEqualTo("GAME_STARTED");

        JsonNode rankingEvent = rankingEvents.poll(5, TimeUnit.SECONDS);
        assertThat(rankingEvent).isNotNull();
        assertThat(rankingEvent.get("type").asText()).isEqualTo("RANKING_UPDATED");

        tvSession.disconnect();
    }
}

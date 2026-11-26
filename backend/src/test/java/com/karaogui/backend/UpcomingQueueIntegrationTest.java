package com.karaogui.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class UpcomingQueueIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired WebSocketStompClient stompClient;

    private JsonNode pollForType(BlockingQueue<JsonNode> queue, String expectedType) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode event = queue.poll(500, TimeUnit.MILLISECONDS);
            if (event != null && expectedType.equals(event.path("type").asText())) {
                return event;
            }
        }
        return null;
    }

    @Test
    void snapshotAndStompBroadcast_trackUpcomingSongsAndPerformers() throws Exception {
        MvcResult tvResult = mvc.perform(post("/api/tv/register"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tvBody = mapper.readTree(tvResult.getResponse().getContentAsString());
        String tvCode = tvBody.get("joinCode").asText();
        String displayToken = tvBody.get("displayToken").asText();

        MvcResult createResult = mvc.perform(post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"host": {"displayName": "Host"}, "tvCode": "%s"}
                        """.formatted(tvCode)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createBody = mapper.readTree(createResult.getResponse().getContentAsString());
        String gameId = createBody.get("gameId").asText();
        String joinCode = createBody.get("joinCode").asText();
        String hostToken = createBody.get("sessionToken").asText();

        MvcResult joinAna = mvc.perform(post("/api/games/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"joinCode": "%s", "player": {"displayName": "Ana"}}
                        """.formatted(joinCode)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode anaBody = mapper.readTree(joinAna.getResponse().getContentAsString());
        String anaToken = anaBody.get("sessionToken").asText();
        String anaId = anaBody.get("you").get("playerId").asText();

        MvcResult joinBob = mvc.perform(post("/api/games/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"joinCode": "%s", "player": {"displayName": "Bob"}}
                        """.formatted(joinCode)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode bobBody = mapper.readTree(joinBob.getResponse().getContentAsString());
        String bobToken = bobBody.get("sessionToken").asText();
        String bobId = bobBody.get("you").get("playerId").asText();

        MvcResult joinCara = mvc.perform(post("/api/games/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"joinCode": "%s", "player": {"displayName": "Cara"}}
                        """.formatted(joinCode)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode caraBody = mapper.readTree(joinCara.getResponse().getContentAsString());
        String caraToken = caraBody.get("sessionToken").asText();
        String caraId = caraBody.get("you").get("playerId").asText();

        MvcResult vidA = mvc.perform(post("/api/videos/local")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"videoUrl": "/videos/song-a.mp4", "songTitle": "Song A", "artist": "Artist A", "durationSeconds": 180}
                        """))
                .andExpect(status().isCreated()).andReturn();
        String videoAId = mapper.readTree(vidA.getResponse().getContentAsString()).get("videoId").asText();

        MvcResult vidB = mvc.perform(post("/api/videos/local")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"videoUrl": "/videos/song-b.mp4", "songTitle": "Song B", "artist": "Artist B", "durationSeconds": 200}
                        """))
                .andExpect(status().isCreated()).andReturn();
        String videoBId = mapper.readTree(vidB.getResponse().getContentAsString()).get("videoId").asText();

        mvc.perform(post("/api/games/" + gameId + "/start")
                .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());

        BlockingQueue<JsonNode> stateEvents = new LinkedBlockingQueue<>();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + displayToken);

        StompSession tvSession = stompClient
                .connectAsync("ws://localhost:" + port + "/ws",
                        (org.springframework.web.socket.WebSocketHttpHeaders) null,
                        connectHeaders, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        tvSession.subscribe("/topic/games/" + gameId + "/state", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return String.class; }
            @Override public void handleFrame(StompHeaders h, Object p) {
                try { stateEvents.add(mapper.readTree((String) p)); } catch (Exception e) { throw new RuntimeException(e); }
            }
        });
        Thread.sleep(200);

        // Queue Song A -> immediately announced, so upcomingQueue is empty
        MvcResult queueARes = mvc.perform(post("/api/games/" + gameId + "/performances")
                .header("Authorization", "Bearer " + hostToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type": "KARAOKE", "videoId": "%s", "performerPlayerIds": ["%s", "%s"]}
                        """.formatted(videoAId, anaId, bobId)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode queueABody = mapper.readTree(queueARes.getResponse().getContentAsString());
        String perfAId = queueABody.get("performanceId").asText();
        String judgeId = queueABody.get("judges").get(0).get("judgePlayerId").asText();
        String judgeToken = judgeId.equals(caraId) ? caraToken : hostToken;

        JsonNode initialQueueEvent = pollForType(stateEvents, "QUEUE_UPDATED");
        assertThat(initialQueueEvent).isNotNull();
        assertThat(initialQueueEvent.get("data").get("upcomingQueue")).isEmpty();

        mvc.perform(get("/api/games/" + gameId)
                .header("Authorization", "Bearer " + displayToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcomingQueue.length()").value(0))
                .andExpect(jsonPath("$.queueNonEmpty").value(false));

        // Queue Song B while Song A is CONFIRMING -> TV gets QUEUE_UPDATED with Song B
        mvc.perform(post("/api/games/" + gameId + "/performances")
                .header("Authorization", "Bearer " + hostToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type": "KARAOKE", "videoId": "%s", "performerPlayerIds": ["%s", "%s"]}
                        """.formatted(videoBId, anaId, bobId)))
                .andExpect(status().isCreated());

        JsonNode queuedEvent = pollForType(stateEvents, "QUEUE_UPDATED");
        assertThat(queuedEvent).isNotNull();
        JsonNode upcoming = queuedEvent.get("data").get("upcomingQueue");
        assertThat(upcoming.size()).isEqualTo(1);
        assertThat(upcoming.get(0).get("songTitle").asText()).isEqualTo("Song B");
        assertThat(upcoming.get(0).get("artist").asText()).isEqualTo("Artist B");
        assertThat(upcoming.get(0).get("performerNames").get(0).asText()).isEqualTo("Ana");
        assertThat(upcoming.get(0).get("performerNames").get(1).asText()).isEqualTo("Bob");

        mvc.perform(get("/api/games/" + gameId)
                .header("Authorization", "Bearer " + displayToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueNonEmpty").value(true))
                .andExpect(jsonPath("$.upcomingQueue.length()").value(1))
                .andExpect(jsonPath("$.upcomingQueue[0].songTitle").value("Song B"))
                .andExpect(jsonPath("$.upcomingQueue[0].performerNames[0]").value("Ana"));

        // Confirm and evaluate Song A -> Song B is announced and removed from upcomingQueue
        mvc.perform(post("/api/games/" + gameId + "/performances/" + perfAId + "/confirm")
                .header("Authorization", "Bearer " + anaToken))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/games/" + gameId + "/performances/" + perfAId + "/confirm")
                .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/games/" + gameId + "/performances/" + perfAId + "/evaluate")
                .header("Authorization", "Bearer " + judgeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"baseline": [{"criterion": "PITCH", "score": 8}]}
                        """))
                .andExpect(status().isNoContent());

        JsonNode drainedEvent = pollForType(stateEvents, "QUEUE_UPDATED");
        assertThat(drainedEvent).isNotNull();
        assertThat(drainedEvent.get("data").get("upcomingQueue")).isEmpty();

        tvSession.disconnect();
    }
}

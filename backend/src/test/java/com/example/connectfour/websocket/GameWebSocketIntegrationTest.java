package com.example.connectfour.websocket;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @AfterEach
    void closeSessions() throws Exception {
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                session.close();
            }
        }
    }

    @Test
    void startsMovesAndAbandonsAGame() throws Exception {
        Connection connection = connect();

        send(connection.session(), """
                {"type":"START_GAME","payload":{"humanColor":"RED","firstPlayer":"HUMAN"}}
                """);
        JsonNode started = connection.handler().awaitMessage(jsonMapper);

        assertEquals("GAME_STATE", started.path("type").asString());
        assertEquals("RED", started.path("payload").path("humanColor").asString());
        assertEquals("HUMAN", started.path("payload").path("firstPlayer").asString());
        assertEquals(6, started.path("payload").path("board").size());
        assertEquals(7, started.path("payload").path("board").get(0).size());
        assertNotNull(UUID.fromString(started.path("payload").path("gameId").asString()));

        send(connection.session(), """
                {"type":"DROP_COUNTER","payload":{"column":3}}
                """);
        JsonNode moved = connection.handler().awaitMessage(jsonMapper);

        assertEquals("GAME_STATE", moved.path("type").asString());
        assertEquals("IN_PROGRESS", moved.path("payload").path("status").asString());
        assertTrue(moved.path("payload").path("computerColumn").isInt());

        send(connection.session(), """
                {"type":"ABANDON_GAME","payload":{}}
                """);
        JsonNode abandoned = connection.handler().awaitMessage(jsonMapper);

        assertEquals("GAME_ABANDONED", abandoned.path("type").asString());
        assertTrue(connection.session().isOpen());
    }

    @Test
    void replacesThePreviousConnectionWhenAGameIsResumed() throws Exception {
        Connection first = connect();
        send(first.session(), """
                {"type":"START_GAME","payload":{"humanColor":"YELLOW","firstPlayer":"COMPUTER"}}
                """);
        JsonNode started = first.handler().awaitMessage(jsonMapper);
        String gameId = started.path("payload").path("gameId").asString();

        Connection resumed = connect();
        send(resumed.session(), """
                {"type":"RESUME_GAME","payload":{"gameId":"%s"}}
                """.formatted(gameId));
        JsonNode restored = resumed.handler().awaitMessage(jsonMapper);

        assertEquals("GAME_STATE", restored.path("type").asString());
        assertEquals(gameId, restored.path("payload").path("gameId").asString());
        assertTrue(first.handler().awaitClosed());
        assertFalse(first.session().isOpen());

        send(resumed.session(), """
                {"type":"DROP_COUNTER","payload":{"column":3}}
                """);
        assertEquals("GAME_STATE", resumed.handler().awaitMessage(jsonMapper).path("type").asString());
    }

    @Test
    void returnsRecoverableProtocolErrorsWithoutClosingTheSocket() throws Exception {
        Connection connection = connect();

        send(connection.session(), "{");
        assertError(connection.handler().awaitMessage(jsonMapper), "MALFORMED_MESSAGE", true);

        send(connection.session(), """
                {"type":"NOT_A_COMMAND","payload":{}}
                """);
        assertError(connection.handler().awaitMessage(jsonMapper), "UNKNOWN_MESSAGE", true);

        send(connection.session(), """
                {"type":"DROP_COUNTER","payload":{"column":0}}
                """);
        assertError(connection.handler().awaitMessage(jsonMapper), "NO_ACTIVE_GAME", true);

        send(connection.session(), """
                {"type":"START_GAME","payload":{"firstPlayer":"HUMAN"}}
                """);
        assertError(connection.handler().awaitMessage(jsonMapper), "INVALID_CONFIGURATION", true);

        assertTrue(connection.session().isOpen());
    }

    @Test
    void reportsAnUnknownGameAsNonRecoverableForResume() throws Exception {
        Connection connection = connect();

        send(connection.session(), """
                {"type":"RESUME_GAME","payload":{"gameId":"%s"}}
                """.formatted(UUID.randomUUID()));
        JsonNode error = connection.handler().awaitMessage(jsonMapper);

        assertError(error, "GAME_NOT_FOUND", false);
        assertTrue(connection.session().isOpen());
    }

    private Connection connect() throws Exception {
        RecordingHandler handler = new RecordingHandler();
        WebSocketSession session = new StandardWebSocketClient()
                .execute(handler, "ws://localhost:%d/ws/game".formatted(port))
                .get(5, SECONDS);
        sessions.add(session);
        return new Connection(session, handler);
    }

    private void send(WebSocketSession session, String payload) throws Exception {
        session.sendMessage(new TextMessage(payload));
    }

    private void assertError(JsonNode response, String code, boolean recoverable) {
        assertEquals("ERROR", response.path("type").asString());
        assertEquals(code, response.path("payload").path("code").asString());
        assertEquals(recoverable, response.path("payload").path("recoverable").asBoolean());
    }

    private record Connection(WebSocketSession session, RecordingHandler handler) {}

    private static final class RecordingHandler extends TextWebSocketHandler {

        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final CountDownLatch closed = new CountDownLatch(1);

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.add(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closed.countDown();
        }

        JsonNode awaitMessage(JsonMapper jsonMapper) throws Exception {
            String message = messages.poll(5, SECONDS);
            assertNotNull(message, "Timed out waiting for a WebSocket message");
            return jsonMapper.readTree(message);
        }

        boolean awaitClosed() throws InterruptedException {
            return closed.await(5, SECONDS);
        }
    }
}

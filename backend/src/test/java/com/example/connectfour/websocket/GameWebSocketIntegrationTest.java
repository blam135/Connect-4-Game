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
    void startsMovesResumesAndAbandonsAComputerGame() throws Exception {
        Connection first = connect();

        send(first.session(), """
                {"type":"START_GAME","payload":{"humanColor":"RED","firstPlayer":"HUMAN"}}
                """);
        JsonNode started = first.handler().awaitMessage(jsonMapper);

        assertEquals("GAME_SESSION", started.path("type").asString());
        assertFalse(started.path("payload").path("playerToken").asString().isBlank());
        JsonNode game = started.path("payload").path("game");
        assertGameShape(game, "COMPUTER", "RED");
        assertEquals("RED", game.path("startingColor").asString());
        assertTrue(game.path("opponentConnected").asBoolean());
        assertTrue(game.path("roomCode").isNull());
        String gameId = game.path("gameId").asString();
        String playerToken = started.path("payload").path("playerToken").asString();

        send(first.session(), """
                {"type":"DROP_COUNTER","payload":{"column":3}}
                """);
        JsonNode moved = first.handler().awaitMessage(jsonMapper);
        assertEquals("GAME_STATE", moved.path("type").asString());
        assertTrue(moved.path("payload").path("computerColumn").isInt());

        Connection resumed = connect();
        send(resumed.session(), """
                {"type":"RESUME_GAME","payload":{"gameId":"%s","playerToken":"%s"}}
                """.formatted(gameId, playerToken));
        JsonNode restored = resumed.handler().awaitMessage(jsonMapper);

        assertEquals("GAME_STATE", restored.path("type").asString());
        assertEquals(gameId, restored.path("payload").path("gameId").asString());
        assertTrue(first.handler().awaitClosed());
        assertFalse(first.session().isOpen());

        send(resumed.session(), """
                {"type":"ABANDON_GAME","payload":{}}
                """);
        JsonNode abandoned = resumed.handler().awaitMessage(jsonMapper);
        assertEquals("GAME_ABANDONED", abandoned.path("type").asString());
        assertEquals("YOU_LEFT", abandoned.path("payload").path("reason").asString());
        assertTrue(resumed.session().isOpen());
    }

    @Test
    void createsJoinsAndBroadcastsMovesToTwoOnlinePlayers() throws Exception {
        OnlineConnections players = createJoinedRoom("RED");

        assertEquals("RED", players.hostStarted().path("game").path("yourColor").asString());
        assertEquals("YELLOW", players.guestStarted().path("game").path("yourColor").asString());
        assertEquals(
                "IN_PROGRESS",
                players.hostJoined().path("payload").path("status").asString());
        assertTrue(players.hostJoined().path("payload").path("opponentConnected").asBoolean());

        send(players.host().session(), """
                {"type":"DROP_COUNTER","payload":{"column":3}}
                """);
        JsonNode hostMove = players.host().handler().awaitMessage(jsonMapper);
        JsonNode guestMove = players.guest().handler().awaitMessage(jsonMapper);

        assertEquals("GAME_STATE", hostMove.path("type").asString());
        assertEquals("YELLOW", hostMove.path("payload").path("currentTurn").asString());
        assertEquals("RED", hostMove.path("payload").path("yourColor").asString());
        assertEquals("YELLOW", guestMove.path("payload").path("yourColor").asString());
        assertEquals(1, countCells(hostMove.path("payload").path("board"), "RED"));

        send(players.host().session(), """
                {"type":"DROP_COUNTER","payload":{"column":4}}
                """);
        assertError(players.host().handler().awaitMessage(jsonMapper), "NOT_YOUR_TURN", true);

        send(players.guest().session(), """
                {"type":"DROP_COUNTER","payload":{"column":4}}
                """);
        JsonNode hostReply = players.host().handler().awaitMessage(jsonMapper);
        JsonNode guestReply = players.guest().handler().awaitMessage(jsonMapper);
        assertEquals("RED", hostReply.path("payload").path("currentTurn").asString());
        assertEquals(1, countCells(hostReply.path("payload").path("board"), "RED"));
        assertEquals(1, countCells(hostReply.path("payload").path("board"), "YELLOW"));
        assertEquals(hostReply.path("payload").path("board"), guestReply.path("payload").path("board"));
    }

    @Test
    void pausesOnDisconnectAndResumesOnlyTheMatchingSeat() throws Exception {
        OnlineConnections players = createJoinedRoom("RED");
        players.guest().session().close();

        JsonNode offline = players.host().handler().awaitMessage(jsonMapper);
        assertEquals("GAME_STATE", offline.path("type").asString());
        assertFalse(offline.path("payload").path("opponentConnected").asBoolean());

        send(players.host().session(), """
                {"type":"DROP_COUNTER","payload":{"column":0}}
                """);
        assertError(players.host().handler().awaitMessage(jsonMapper), "OPPONENT_OFFLINE", true);

        Connection resumedGuest = connect();
        send(resumedGuest.session(), """
                {"type":"RESUME_GAME","payload":{"gameId":"%s","playerToken":"%s"}}
                """.formatted(players.gameId(), players.guestToken()));

        JsonNode guestRestored = resumedGuest.handler().awaitMessage(jsonMapper);
        JsonNode hostPresence = players.host().handler().awaitMessage(jsonMapper);
        assertEquals("GAME_STATE", guestRestored.path("type").asString());
        assertEquals("YELLOW", guestRestored.path("payload").path("yourColor").asString());
        assertTrue(guestRestored.path("payload").path("opponentConnected").asBoolean());
        assertTrue(hostPresence.path("payload").path("opponentConnected").asBoolean());
        assertTrue(players.host().session().isOpen());
    }

    @Test
    void reconnectingOneSeatDoesNotReplaceItsOpponent() throws Exception {
        OnlineConnections players = createJoinedRoom("RED");
        Connection resumedHost = connect();

        send(resumedHost.session(), """
                {"type":"RESUME_GAME","payload":{"gameId":"%s","playerToken":"%s"}}
                """.formatted(players.gameId(), players.hostToken()));

        assertEquals(
                "GAME_STATE", resumedHost.handler().awaitMessage(jsonMapper).path("type").asString());
        JsonNode guestPresence = players.guest().handler().awaitMessage(jsonMapper);
        assertEquals("GAME_STATE", guestPresence.path("type").asString());
        assertTrue(guestPresence.path("payload").path("opponentConnected").asBoolean());
        assertTrue(players.host().handler().awaitClosed());
        assertFalse(players.host().session().isOpen());
        assertTrue(players.guest().session().isOpen());
        assertTrue(players.guest().handler().awaitNoMessage());
    }

    @Test
    void abandoningAnOnlineGameNotifiesAndUnbindsBothPlayers() throws Exception {
        OnlineConnections players = createJoinedRoom("RED");

        send(players.guest().session(), """
                {"type":"ABANDON_GAME","payload":{}}
                """);

        JsonNode guestMessage = players.guest().handler().awaitMessage(jsonMapper);
        JsonNode hostMessage = players.host().handler().awaitMessage(jsonMapper);
        assertEquals("YOU_LEFT", guestMessage.path("payload").path("reason").asString());
        assertEquals("OPPONENT_LEFT", hostMessage.path("payload").path("reason").asString());

        send(players.host().session(), """
                {"type":"DROP_COUNTER","payload":{"column":0}}
                """);
        assertError(players.host().handler().awaitMessage(jsonMapper), "NO_ACTIVE_GAME", true);
    }

    @Test
    void returnsStableRoomAndCredentialErrors() throws Exception {
        Connection connection = connect();

        send(connection.session(), """
                {"type":"JOIN_ONLINE_GAME","payload":{"roomCode":"ABC123"}}
                """);
        assertError(connection.handler().awaitMessage(jsonMapper), "ROOM_NOT_FOUND", true);

        Connection host = connect();
        send(host.session(), """
                {"type":"CREATE_ONLINE_GAME","payload":{"hostColor":"YELLOW"}}
                """);
        JsonNode started = host.handler().awaitMessage(jsonMapper).path("payload");

        send(connection.session(), """
                {"type":"RESUME_GAME","payload":{"gameId":"%s","playerToken":"wrong"}}
                """.formatted(started.path("game").path("gameId").asString()));
        assertError(
                connection.handler().awaitMessage(jsonMapper), "INVALID_PLAYER_TOKEN", false);
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

    private OnlineConnections createJoinedRoom(String hostColor) throws Exception {
        Connection host = connect();
        send(host.session(), """
                {"type":"CREATE_ONLINE_GAME","payload":{"hostColor":"%s"}}
                """.formatted(hostColor));
        JsonNode hostMessage = host.handler().awaitMessage(jsonMapper);
        assertEquals("GAME_SESSION", hostMessage.path("type").asString());
        JsonNode hostStarted = hostMessage.path("payload");
        String roomCode = hostStarted.path("game").path("roomCode").asString();

        Connection guest = connect();
        send(guest.session(), """
                {"type":"JOIN_ONLINE_GAME","payload":{"roomCode":"  %s  "}}
                """.formatted(roomCode.toLowerCase()));
        JsonNode guestMessage = guest.handler().awaitMessage(jsonMapper);
        JsonNode hostJoined = host.handler().awaitMessage(jsonMapper);
        assertEquals("GAME_SESSION", guestMessage.path("type").asString());
        assertEquals("GAME_STATE", hostJoined.path("type").asString());

        return new OnlineConnections(
                host,
                guest,
                hostStarted.path("game").path("gameId").asString(),
                hostStarted.path("playerToken").asString(),
                guestMessage.path("payload").path("playerToken").asString(),
                hostStarted,
                guestMessage.path("payload"),
                hostJoined);
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

    private void assertGameShape(JsonNode game, String mode, String yourColor) {
        assertNotNull(UUID.fromString(game.path("gameId").asString()));
        assertEquals(mode, game.path("mode").asString());
        assertEquals(yourColor, game.path("yourColor").asString());
        assertEquals(6, game.path("board").size());
        assertEquals(7, game.path("board").get(0).size());
        assertFalse(game.path("status").isMissingNode());
        assertFalse(game.path("startingColor").isMissingNode());
        assertFalse(game.path("currentTurn").isMissingNode());
        assertFalse(game.path("opponentConnected").isMissingNode());
        assertFalse(game.path("computerColumn").isMissingNode());
    }

    private int countCells(JsonNode board, String cell) {
        int count = 0;
        for (JsonNode row : board) {
            for (JsonNode candidate : row) {
                if (cell.equals(candidate.asString())) {
                    count++;
                }
            }
        }
        return count;
    }

    private void assertError(JsonNode response, String code, boolean recoverable) {
        assertEquals("ERROR", response.path("type").asString());
        assertEquals(code, response.path("payload").path("code").asString());
        assertEquals(recoverable, response.path("payload").path("recoverable").asBoolean());
    }

    private record Connection(WebSocketSession session, RecordingHandler handler) {}

    private record OnlineConnections(
            Connection host,
            Connection guest,
            String gameId,
            String hostToken,
            String guestToken,
            JsonNode hostStarted,
            JsonNode guestStarted,
            JsonNode hostJoined) {}

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

        boolean awaitNoMessage() throws InterruptedException {
            return messages.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS) == null;
        }
    }
}

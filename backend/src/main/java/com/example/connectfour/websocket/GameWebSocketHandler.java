package com.example.connectfour.websocket;

import com.example.connectfour.game.FirstPlayer;
import com.example.connectfour.game.GameErrorCode;
import com.example.connectfour.game.GameException;
import com.example.connectfour.game.GameService;
import com.example.connectfour.game.GameSnapshot;
import com.example.connectfour.game.PlayerColor;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameWebSocketHandler.class);
    private static final String GAME_ID_ATTRIBUTE = GameWebSocketHandler.class.getName() + ".gameId";

    private final GameService gameService;
    private final GameConnectionRegistry connections;
    private final JsonMapper jsonMapper;

    public GameWebSocketHandler(
            GameService gameService,
            GameConnectionRegistry connections,
            JsonMapper jsonMapper) {
        this.gameService = gameService;
        this.connections = connections;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {
        ClientEnvelope message;
        try {
            message = jsonMapper.readValue(textMessage.getPayload(), ClientEnvelope.class);
        } catch (JacksonException exception) {
            sendError(session, "MALFORMED_MESSAGE", "Message must be valid JSON", true);
            return;
        }

        if (message == null || message.type() == null || message.type().isBlank()) {
            sendError(session, "INVALID_MESSAGE", "Message type is required", true);
            return;
        }

        try {
            dispatch(session, message);
        } catch (CommandHandledException ignored) {
            // The command has already produced its response.
        } catch (TransportException exception) {
            sendError(session, exception.code, exception.getMessage(), exception.recoverable);
        } catch (JacksonException | IllegalArgumentException exception) {
            sendError(session, "INVALID_MESSAGE", "Message payload is invalid", true);
        } catch (GameException exception) {
            sendError(
                    session,
                    exception.getCode().name(),
                    exception.getMessage(),
                    isRecoverable(exception.getCode()));
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.error("Unexpected WebSocket command failure", exception);
            sendError(session, "INTERNAL_ERROR", "The server could not process the message", false);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID gameId = boundGameId(session);
        if (gameId != null) {
            connections.detach(gameId, session);
        }
    }

    private void dispatch(WebSocketSession session, ClientEnvelope message) throws Exception {
        switch (message.type()) {
            case "START_GAME" -> startGame(session, message.payload());
            case "RESUME_GAME" -> resumeGame(session, message.payload());
            case "DROP_COUNTER" -> dropCounter(session, message.payload());
            case "ABANDON_GAME" -> abandonGame(session);
            default -> sendError(
                    session,
                    "UNKNOWN_MESSAGE",
                    "Unsupported message type: " + message.type(),
                    true);
        }
    }

    private void startGame(WebSocketSession session, JsonNode payload) throws Exception {
        ensureConnectionIsUnbound(session);
        StartGamePayload command = requiredPayload(payload, StartGamePayload.class);
        GameSnapshot game = gameService.startGame(command.humanColor(), command.firstPlayer());
        bind(session, game.gameId());
        send(session, new ServerEnvelope("GAME_STATE", game));
    }

    private void resumeGame(WebSocketSession session, JsonNode payload) throws Exception {
        ensureConnectionIsUnbound(session);
        ResumeGamePayload command = requiredPayload(payload, ResumeGamePayload.class);
        if (command.gameId() == null) {
            throw new IllegalArgumentException("Game ID is required");
        }
        GameSnapshot game = gameService.resumeGame(command.gameId());
        bind(session, game.gameId());
        send(session, new ServerEnvelope("GAME_STATE", game));
    }

    private void dropCounter(WebSocketSession session, JsonNode payload) throws Exception {
        UUID gameId = requireActiveGame(session);
        DropCounterPayload command = requiredPayload(payload, DropCounterPayload.class);
        if (command.column() == null) {
            throw new IllegalArgumentException("Column is required");
        }
        GameSnapshot game = gameService.dropCounter(gameId, command.column());
        send(session, new ServerEnvelope("GAME_STATE", game));
    }

    private void abandonGame(WebSocketSession session) throws Exception {
        UUID gameId = requireActiveGame(session);
        gameService.abandonGame(gameId);
        connections.detach(gameId, session);
        session.getAttributes().remove(GAME_ID_ATTRIBUTE);
        send(session, new ServerEnvelope("GAME_ABANDONED", Map.of()));
    }

    private <T> T requiredPayload(JsonNode payload, Class<T> payloadType) throws JacksonException {
        if (payload == null || payload.isNull()) {
            throw new IllegalArgumentException("Payload is required");
        }
        return jsonMapper.treeToValue(payload, payloadType);
    }

    private void ensureConnectionIsUnbound(WebSocketSession session) throws IOException {
        if (boundGameId(session) != null) {
            sendError(
                    session,
                    "CONNECTION_ALREADY_BOUND",
                    "Abandon the current game before starting or resuming another",
                    true);
            throw new CommandHandledException();
        }
    }

    private UUID requireActiveGame(WebSocketSession session) {
        UUID gameId = boundGameId(session);
        if (gameId == null) {
            throw new TransportException("NO_ACTIVE_GAME", "Start or resume a game first", true);
        }
        if (!connections.isActive(gameId, session)) {
            throw new TransportException("STALE_CONNECTION", "This game moved to another connection", false);
        }
        return gameId;
    }

    private void bind(WebSocketSession session, UUID gameId) {
        session.getAttributes().put(GAME_ID_ATTRIBUTE, gameId);
        connections.attach(gameId, session);
    }

    private UUID boundGameId(WebSocketSession session) {
        Object gameId = session.getAttributes().get(GAME_ID_ATTRIBUTE);
        return gameId instanceof UUID uuid ? uuid : null;
    }

    private boolean isRecoverable(GameErrorCode code) {
        return switch (code) {
            case INVALID_CONFIGURATION, INVALID_COLUMN, COLUMN_FULL -> true;
            case GAME_NOT_FOUND, GAME_FINISHED -> false;
        };
    }

    private void sendError(
            WebSocketSession session,
            String code,
            String message,
            boolean recoverable) throws IOException {
        send(session, new ServerEnvelope("ERROR", new ErrorPayload(code, message, recoverable)));
    }

    private void send(WebSocketSession session, ServerEnvelope message) throws IOException {
        String payload;
        try {
            payload = jsonMapper.writeValueAsString(message);
        } catch (JacksonException exception) {
            throw new IOException("Could not serialize WebSocket message", exception);
        }

        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(payload));
            }
        }
    }

    record ClientEnvelope(String type, JsonNode payload) {}

    record StartGamePayload(PlayerColor humanColor, FirstPlayer firstPlayer) {}

    record ResumeGamePayload(UUID gameId) {}

    record DropCounterPayload(Integer column) {}

    record ServerEnvelope(String type, Object payload) {}

    record ErrorPayload(String code, String message, boolean recoverable) {}

    private static final class CommandHandledException extends RuntimeException {}

    private static final class TransportException extends RuntimeException {

        private final String code;
        private final boolean recoverable;

        private TransportException(String code, String message, boolean recoverable) {
            super(message);
            this.code = code;
            this.recoverable = recoverable;
        }
    }
}

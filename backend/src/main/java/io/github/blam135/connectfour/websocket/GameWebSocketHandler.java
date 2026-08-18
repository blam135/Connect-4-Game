package io.github.blam135.connectfour.websocket;

import io.github.blam135.connectfour.dto.GameAccess;
import io.github.blam135.connectfour.dto.GameSnapshot;
import io.github.blam135.connectfour.exception.GameErrorCode;
import io.github.blam135.connectfour.exception.GameException;
import io.github.blam135.connectfour.model.FirstPlayer;
import io.github.blam135.connectfour.model.GameMode;
import io.github.blam135.connectfour.model.PlayerColor;
import io.github.blam135.connectfour.service.GameService;
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
    private static final String PLAYER_COLOR_ATTRIBUTE =
            GameWebSocketHandler.class.getName() + ".playerColor";

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
        PlayerColor playerColor = boundPlayerColor(session);
        if (gameId == null || playerColor == null) {
            return;
        }

        synchronized (connections.gameLock(gameId)) {
            if (!connections.detach(gameId, playerColor, session)) {
                return;
            }
            try {
                Map<PlayerColor, GameSnapshot> states =
                        gameService.setConnected(gameId, playerColor, false);
                broadcast(gameId, states, null);
            } catch (GameException exception) {
                if (exception.getCode() != GameErrorCode.GAME_NOT_FOUND) {
                    LOGGER.warn("Could not update player presence after disconnect", exception);
                }
            }
        }
    }

    private void dispatch(WebSocketSession session, ClientEnvelope message) throws Exception {
        switch (message.type()) {
            case "START_GAME" -> startGame(session, message.payload());
            case "CREATE_ONLINE_GAME" -> createOnlineGame(session, message.payload());
            case "JOIN_ONLINE_GAME" -> joinOnlineGame(session, message.payload());
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
        GameAccess access = gameService.startGame(command.humanColor(), command.firstPlayer());
        synchronized (connections.gameLock(access.game().gameId())) {
            bind(session, access.game());
            send(session, new ServerEnvelope(
                    "GAME_SESSION", new GameSessionPayload(access.playerToken(), access.game())));
        }
    }

    private void createOnlineGame(WebSocketSession session, JsonNode payload) throws Exception {
        ensureConnectionIsUnbound(session);
        CreateOnlineGamePayload command = requiredPayload(payload, CreateOnlineGamePayload.class);
        GameAccess access = gameService.createOnlineGame(command.hostColor());
        synchronized (connections.gameLock(access.game().gameId())) {
            Map<PlayerColor, GameSnapshot> states = activate(session, access.game());
            send(session, new ServerEnvelope(
                    "GAME_SESSION",
                    new GameSessionPayload(
                            access.playerToken(), states.get(access.game().yourColor()))));
        }
    }

    private void joinOnlineGame(WebSocketSession session, JsonNode payload) throws Exception {
        ensureConnectionIsUnbound(session);
        JoinOnlineGamePayload command = requiredPayload(payload, JoinOnlineGamePayload.class);
        UUID gameId = gameService.onlineGameId(command.roomCode());
        synchronized (connections.gameLock(gameId)) {
            GameAccess access = gameService.joinOnlineGame(gameId, command.roomCode());
            Map<PlayerColor, GameSnapshot> states = activate(session, access.game());
            send(session, new ServerEnvelope(
                    "GAME_SESSION",
                    new GameSessionPayload(
                            access.playerToken(), states.get(access.game().yourColor()))));
            broadcast(access.game().gameId(), states, access.game().yourColor());
        }
    }

    private void resumeGame(WebSocketSession session, JsonNode payload) throws Exception {
        ensureConnectionIsUnbound(session);
        ResumeGamePayload command = requiredPayload(payload, ResumeGamePayload.class);
        if (command.gameId() == null) {
            throw new IllegalArgumentException("Game ID is required");
        }
        synchronized (connections.gameLock(command.gameId())) {
            GameSnapshot game = gameService.resumeGame(command.gameId(), command.playerToken());
            Map<PlayerColor, GameSnapshot> states = activate(session, game);
            send(session, new ServerEnvelope("GAME_STATE", states.get(game.yourColor())));
            if (game.mode() == GameMode.ONLINE) {
                broadcast(game.gameId(), states, game.yourColor());
            }
        }
    }

    private void dropCounter(WebSocketSession session, JsonNode payload) throws Exception {
        DropCounterPayload command = requiredPayload(payload, DropCounterPayload.class);
        if (command.column() == null) {
            throw new IllegalArgumentException("Column is required");
        }
        UUID gameId = requireBoundGameId(session);
        synchronized (connections.gameLock(gameId)) {
            ActiveGame activeGame = requireActiveGame(session);
            GameSnapshot game = gameService.dropCounter(
                    activeGame.gameId(), activeGame.playerColor(), command.column());
            if (game.mode() == GameMode.COMPUTER) {
                send(session, new ServerEnvelope("GAME_STATE", game));
            } else {
                broadcast(activeGame.gameId(), gameService.snapshots(activeGame.gameId()), null);
            }
        }
    }

    private void abandonGame(WebSocketSession session) throws Exception {
        UUID gameId = requireBoundGameId(session);
        synchronized (connections.gameLock(gameId)) {
            ActiveGame activeGame = requireActiveGame(session);
            gameService.abandonGame(activeGame.gameId(), activeGame.playerColor());
            Map<PlayerColor, WebSocketSession> gameConnections =
                    connections.detachGame(activeGame.gameId());
            for (Map.Entry<PlayerColor, WebSocketSession> entry : gameConnections.entrySet()) {
                WebSocketSession playerSession = entry.getValue();
                unbind(playerSession);
                String reason = entry.getKey() == activeGame.playerColor()
                        ? "YOU_LEFT"
                        : "OPPONENT_LEFT";
                try {
                    send(playerSession, new ServerEnvelope(
                            "GAME_ABANDONED", new GameAbandonedPayload(reason)));
                } catch (IOException | IllegalStateException exception) {
                    LOGGER.debug("Could not send game-abandoned notification", exception);
                }
            }
        }
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

    private ActiveGame requireActiveGame(WebSocketSession session) {
        UUID gameId = boundGameId(session);
        PlayerColor playerColor = boundPlayerColor(session);
        if (gameId == null || playerColor == null) {
            throw new TransportException("NO_ACTIVE_GAME", "Start or resume a game first", true);
        }
        if (!connections.isActive(gameId, playerColor, session)) {
            throw new TransportException(
                    "STALE_CONNECTION", "This player moved to another connection", false);
        }
        return new ActiveGame(gameId, playerColor);
    }

    private UUID requireBoundGameId(WebSocketSession session) {
        UUID gameId = boundGameId(session);
        if (gameId == null) {
            throw new TransportException("NO_ACTIVE_GAME", "Start or resume a game first", true);
        }
        return gameId;
    }

    private void bind(WebSocketSession session, GameSnapshot game) {
        session.getAttributes().put(GAME_ID_ATTRIBUTE, game.gameId());
        session.getAttributes().put(PLAYER_COLOR_ATTRIBUTE, game.yourColor());
        connections.attach(game.gameId(), game.yourColor(), session);
    }

    private Map<PlayerColor, GameSnapshot> activate(
            WebSocketSession session, GameSnapshot game) {
        bind(session, game);
        try {
            return gameService.setConnected(game.gameId(), game.yourColor(), true);
        } catch (RuntimeException exception) {
            connections.detach(game.gameId(), game.yourColor(), session);
            unbind(session);
            throw exception;
        }
    }

    private void unbind(WebSocketSession session) {
        session.getAttributes().remove(GAME_ID_ATTRIBUTE);
        session.getAttributes().remove(PLAYER_COLOR_ATTRIBUTE);
    }

    private UUID boundGameId(WebSocketSession session) {
        Object gameId = session.getAttributes().get(GAME_ID_ATTRIBUTE);
        return gameId instanceof UUID uuid ? uuid : null;
    }

    private PlayerColor boundPlayerColor(WebSocketSession session) {
        Object playerColor = session.getAttributes().get(PLAYER_COLOR_ATTRIBUTE);
        return playerColor instanceof PlayerColor color ? color : null;
    }

    private void broadcast(
            UUID gameId,
            Map<PlayerColor, GameSnapshot> states,
            PlayerColor excludedColor) {
        for (Map.Entry<PlayerColor, WebSocketSession> entry :
                connections.connections(gameId).entrySet()) {
            if (entry.getKey() != excludedColor) {
                GameSnapshot state = states.get(entry.getKey());
                if (state != null) {
                    try {
                        send(entry.getValue(), new ServerEnvelope("GAME_STATE", state));
                    } catch (IOException | IllegalStateException exception) {
                        LOGGER.debug("Could not send game-state broadcast", exception);
                    }
                }
            }
        }
    }

    private boolean isRecoverable(GameErrorCode code) {
        return switch (code) {
            case INVALID_CONFIGURATION,
                    INVALID_COLUMN,
                    COLUMN_FULL,
                    ROOM_NOT_FOUND,
                    ROOM_FULL,
                    NOT_YOUR_TURN,
                    OPPONENT_OFFLINE -> true;
            case GAME_NOT_FOUND, INVALID_PLAYER_TOKEN, GAME_FINISHED -> false;
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

    record CreateOnlineGamePayload(PlayerColor hostColor) {}

    record JoinOnlineGamePayload(String roomCode) {}

    record ResumeGamePayload(UUID gameId, String playerToken) {}

    record DropCounterPayload(Integer column) {}

    record ServerEnvelope(String type, Object payload) {}

    record GameSessionPayload(String playerToken, GameSnapshot game) {}

    record GameAbandonedPayload(String reason) {}

    record ErrorPayload(String code, String message, boolean recoverable) {}

    private record ActiveGame(UUID gameId, PlayerColor playerColor) {}

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

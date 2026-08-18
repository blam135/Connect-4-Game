package io.github.blam135.connectfour.websocket;

import io.github.blam135.connectfour.model.PlayerColor;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Component
class GameConnectionRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameConnectionRegistry.class);
    private static final int GAME_LOCK_COUNT = 64;

    private final ConcurrentMap<PlayerConnection, WebSocketSession> activeConnections =
            new ConcurrentHashMap<>();
    private final Object[] gameLocks = createGameLocks();

    Object gameLock(UUID gameId) {
        return gameLocks[Math.floorMod(gameId.hashCode(), gameLocks.length)];
    }

    void attach(UUID gameId, PlayerColor playerColor, WebSocketSession session) {
        PlayerConnection key = new PlayerConnection(gameId, playerColor);
        WebSocketSession previous = activeConnections.put(key, session);
        if (previous != null && previous != session && previous.isOpen()) {
            try {
                previous.close(CloseStatus.NORMAL.withReason("Game resumed on another connection"));
            } catch (IOException exception) {
                LOGGER.warn("Could not close replaced WebSocket connection", exception);
            }
        }
    }

    boolean isActive(UUID gameId, PlayerColor playerColor, WebSocketSession session) {
        return activeConnections.get(new PlayerConnection(gameId, playerColor)) == session;
    }

    boolean detach(UUID gameId, PlayerColor playerColor, WebSocketSession session) {
        return activeConnections.remove(new PlayerConnection(gameId, playerColor), session);
    }

    Map<PlayerColor, WebSocketSession> connections(UUID gameId) {
        Map<PlayerColor, WebSocketSession> result = new EnumMap<>(PlayerColor.class);
        activeConnections.forEach((key, session) -> {
            if (key.gameId().equals(gameId)) {
                result.put(key.playerColor(), session);
            }
        });
        return Map.copyOf(result);
    }

    Map<PlayerColor, WebSocketSession> detachGame(UUID gameId) {
        Map<PlayerColor, WebSocketSession> result = connections(gameId);
        result.forEach((color, session) ->
                activeConnections.remove(new PlayerConnection(gameId, color), session));
        return result;
    }

    private Object[] createGameLocks() {
        Object[] locks = new Object[GAME_LOCK_COUNT];
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new Object();
        }
        return locks;
    }

    private record PlayerConnection(UUID gameId, PlayerColor playerColor) {}
}

package com.example.connectfour.websocket;

import java.io.IOException;
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

    private final ConcurrentMap<UUID, WebSocketSession> activeConnections = new ConcurrentHashMap<>();

    void attach(UUID gameId, WebSocketSession session) {
        WebSocketSession previous = activeConnections.put(gameId, session);
        if (previous != null && previous != session && previous.isOpen()) {
            try {
                previous.close(CloseStatus.NORMAL.withReason("Game resumed on another connection"));
            } catch (IOException exception) {
                LOGGER.warn("Could not close replaced WebSocket connection", exception);
            }
        }
    }

    boolean isActive(UUID gameId, WebSocketSession session) {
        return activeConnections.get(gameId) == session;
    }

    void detach(UUID gameId, WebSocketSession session) {
        activeConnections.remove(gameId, session);
    }
}

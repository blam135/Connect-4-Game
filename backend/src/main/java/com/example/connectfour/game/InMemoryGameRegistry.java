package com.example.connectfour.game;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryGameRegistry {

    private final ConcurrentMap<UUID, GameSession> sessions = new ConcurrentHashMap<>();

    void register(GameSession session) {
        sessions.put(session.id(), session);
    }

    Optional<GameSession> find(UUID gameId) {
        return Optional.ofNullable(sessions.get(gameId));
    }

    boolean remove(UUID gameId, GameSession session) {
        return sessions.remove(gameId, session);
    }
}

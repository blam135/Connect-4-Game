package com.example.connectfour.game.service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryGameRegistry {

    private final ConcurrentMap<UUID, GameSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> roomGames = new ConcurrentHashMap<>();

    void register(GameSession session) {
        sessions.put(session.id(), session);
    }

    boolean registerOnline(GameSession session) {
        if (roomGames.putIfAbsent(session.roomCode(), session.id()) != null) {
            return false;
        }
        sessions.put(session.id(), session);
        return true;
    }

    Optional<GameSession> find(UUID gameId) {
        return Optional.ofNullable(sessions.get(gameId));
    }

    Optional<GameSession> findByRoomCode(String roomCode) {
        UUID gameId = roomGames.get(roomCode);
        return gameId == null ? Optional.empty() : find(gameId);
    }

    boolean remove(UUID gameId, GameSession session) {
        if (!sessions.remove(gameId, session)) {
            return false;
        }
        if (session.roomCode() != null) {
            roomGames.remove(session.roomCode(), gameId);
        }
        return true;
    }
}

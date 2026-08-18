package com.example.connectfour.repository;

import com.example.connectfour.model.GameSession;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryGameRepository {

    private final ConcurrentMap<UUID, GameSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> roomGames = new ConcurrentHashMap<>();

    public void register(GameSession session) {
        sessions.put(session.id(), session);
    }

    public boolean registerOnline(GameSession session) {
        if (roomGames.putIfAbsent(session.roomCode(), session.id()) != null) {
            return false;
        }
        sessions.put(session.id(), session);
        return true;
    }

    public Optional<GameSession> find(UUID gameId) {
        return Optional.ofNullable(sessions.get(gameId));
    }

    public Optional<GameSession> findByRoomCode(String roomCode) {
        UUID gameId = roomGames.get(roomCode);
        return gameId == null ? Optional.empty() : find(gameId);
    }

    public boolean remove(UUID gameId, GameSession session) {
        if (!sessions.remove(gameId, session)) {
            return false;
        }
        if (session.roomCode() != null) {
            roomGames.remove(session.roomCode(), gameId);
        }
        return true;
    }
}

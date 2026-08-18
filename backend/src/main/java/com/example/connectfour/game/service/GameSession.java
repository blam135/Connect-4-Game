package com.example.connectfour.game.service;

import com.example.connectfour.core.Board;
import com.example.connectfour.game.type.GameMode;
import com.example.connectfour.game.type.GameStatus;
import com.example.connectfour.game.type.PlayerColor;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

final class GameSession {

    private final UUID id;
    private final Board board;
    private final GameMode mode;
    private final PlayerColor startingColor;
    private final PlayerColor computerHumanColor;
    private final String roomCode;
    private final Map<PlayerColor, String> playerTokens = new EnumMap<>(PlayerColor.class);
    private final EnumSet<PlayerColor> connectedPlayers = EnumSet.noneOf(PlayerColor.class);
    private GameStatus status;
    private PlayerColor currentTurn;

    private GameSession(
            UUID id,
            Board board,
            GameMode mode,
            PlayerColor startingColor,
            PlayerColor computerHumanColor,
            String roomCode,
            GameStatus status,
            PlayerColor currentTurn) {
        this.id = id;
        this.board = board;
        this.mode = mode;
        this.startingColor = startingColor;
        this.computerHumanColor = computerHumanColor;
        this.roomCode = roomCode;
        this.status = status;
        this.currentTurn = currentTurn;
    }

    static GameSession computer(
            UUID id,
            Board board,
            PlayerColor humanColor,
            PlayerColor startingColor,
            String playerToken) {
        GameSession session = new GameSession(
                id,
                board,
                GameMode.COMPUTER,
                startingColor,
                humanColor,
                null,
                GameStatus.IN_PROGRESS,
                humanColor);
        session.playerTokens.put(humanColor, playerToken);
        return session;
    }

    static GameSession online(
            UUID id,
            Board board,
            PlayerColor hostColor,
            String hostToken,
            String roomCode) {
        GameSession session = new GameSession(
                id,
                board,
                GameMode.ONLINE,
                PlayerColor.RED,
                null,
                roomCode,
                GameStatus.WAITING_FOR_OPPONENT,
                PlayerColor.RED);
        session.playerTokens.put(hostColor, hostToken);
        return session;
    }

    UUID id() {
        return id;
    }

    Board board() {
        return board;
    }

    GameMode mode() {
        return mode;
    }

    PlayerColor startingColor() {
        return startingColor;
    }

    PlayerColor computerHumanColor() {
        return computerHumanColor;
    }

    PlayerColor computerColor() {
        return computerHumanColor.opponent();
    }

    String roomCode() {
        return roomCode;
    }

    GameStatus status() {
        return status;
    }

    void setStatus(GameStatus status) {
        this.status = status;
    }

    PlayerColor currentTurn() {
        return currentTurn;
    }

    void setCurrentTurn(PlayerColor currentTurn) {
        this.currentTurn = currentTurn;
    }

    boolean hasPlayer(PlayerColor color) {
        return playerTokens.containsKey(color);
    }

    boolean hasBothOnlinePlayers() {
        return playerTokens.size() == 2;
    }

    void addPlayer(PlayerColor color, String token) {
        playerTokens.put(color, token);
    }

    PlayerColor authenticate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return playerTokens.entrySet().stream()
                .filter(entry -> entry.getValue().equals(token))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    void setConnected(PlayerColor color, boolean connected) {
        if (connected) {
            connectedPlayers.add(color);
        } else {
            connectedPlayers.remove(color);
        }
    }

    boolean isConnected(PlayerColor color) {
        return connectedPlayers.contains(color);
    }
}

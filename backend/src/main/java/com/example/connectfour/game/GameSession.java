package com.example.connectfour.game;

import com.example.connectfour.core.Board;
import java.util.UUID;

final class GameSession {

    private final UUID id;
    private final Board board;
    private final PlayerColor humanColor;
    private final FirstPlayer firstPlayer;
    private GameStatus status;

    GameSession(
            UUID id,
            Board board,
            PlayerColor humanColor,
            FirstPlayer firstPlayer,
            GameStatus status) {
        this.id = id;
        this.board = board;
        this.humanColor = humanColor;
        this.firstPlayer = firstPlayer;
        this.status = status;
    }

    UUID id() {
        return id;
    }

    Board board() {
        return board;
    }

    PlayerColor humanColor() {
        return humanColor;
    }

    PlayerColor computerColor() {
        return humanColor.opponent();
    }

    FirstPlayer firstPlayer() {
        return firstPlayer;
    }

    GameStatus status() {
        return status;
    }

    void setStatus(GameStatus status) {
        this.status = status;
    }
}

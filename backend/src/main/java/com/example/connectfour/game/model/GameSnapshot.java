package com.example.connectfour.game.model;

import com.example.connectfour.game.type.Cell;
import com.example.connectfour.game.type.GameMode;
import com.example.connectfour.game.type.GameStatus;
import com.example.connectfour.game.type.PlayerColor;

import java.util.List;
import java.util.UUID;

public record GameSnapshot(
        UUID gameId,
        GameMode mode,
        List<List<Cell>> board,
        GameStatus status,
        PlayerColor yourColor,
        PlayerColor startingColor,
        PlayerColor currentTurn,
        String roomCode,
        boolean opponentConnected,
        Integer computerColumn) {

    public GameSnapshot {
        board = board.stream().map(List::copyOf).toList();
    }
}

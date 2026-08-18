package com.example.connectfour.dto;

import com.example.connectfour.model.Cell;
import com.example.connectfour.model.GameMode;
import com.example.connectfour.model.GameStatus;
import com.example.connectfour.model.PlayerColor;

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

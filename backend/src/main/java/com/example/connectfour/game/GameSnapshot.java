package com.example.connectfour.game;

import java.util.List;
import java.util.UUID;

public record GameSnapshot(
        UUID gameId,
        List<List<Cell>> board,
        GameStatus status,
        PlayerColor humanColor,
        FirstPlayer firstPlayer,
        Integer computerColumn) {

    public GameSnapshot {
        board = board.stream().map(List::copyOf).toList();
    }
}

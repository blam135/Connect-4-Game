package io.github.blam135.connectfour.dto;

import io.github.blam135.connectfour.model.Cell;
import io.github.blam135.connectfour.model.GameMode;
import io.github.blam135.connectfour.model.GameStatus;
import io.github.blam135.connectfour.model.PlayerColor;

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

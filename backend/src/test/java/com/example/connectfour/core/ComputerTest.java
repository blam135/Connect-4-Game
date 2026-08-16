package com.example.connectfour.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ComputerTest {

    @Test
    void reportsTerminalScoresForBothPlayers() {
        Computer computer = new Computer();

        int[] red = compute(computer, board("rrrr..."), true);
        int[] yellow = compute(computer, board("yyyy..."), false);

        assertArrayEquals(new int[] {-1, 10000}, red);
        assertArrayEquals(new int[] {-1, -10000}, yellow);
    }

    @Test
    void takesAnImmediateWinningMoveForBothPlayers() {
        int[] red = compute(new Computer(), board("rrr.yyy"), true);
        int[] yellow = compute(new Computer(), board("yyy.rrr"), false);

        assertArrayEquals(new int[] {3, 10000}, red);
        assertArrayEquals(new int[] {3, -10000}, yellow);
    }

    @Test
    void blocksAnImmediateWinningMoveForBothPlayers() {
        int[] red = compute(new Computer(), board("yyy.r..", "....r.."), true);
        int[] yellow = compute(new Computer(), board("rrr.y..", "....y.."), false);

        assertEquals(3, red[0]);
        assertEquals(3, yellow[0]);
    }

    private int[] compute(Computer computer, Board board, boolean maxPlayer) {
        return computer.computeColumn(
                true,
                board,
                4,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                maxPlayer);
    }

    private Board board(String... rows) {
        StringBuilder state = new StringBuilder();
        for (int row = 0; row < 6; row++) {
            if (row > 0) {
                state.append(',');
            }
            state.append(row < rows.length ? rows[row] : ".......");
        }
        return new Board(state.toString());
    }
}

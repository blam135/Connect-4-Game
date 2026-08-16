package com.example.connectfour.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BoardTest {

    @Test
    void placesCountersFromTheBottomOfAColumn() {
        Board board = emptyBoard();

        assertTrue(board.putCounter(2, 'r'));
        assertTrue(board.putCounter(2, 'y'));

        char[][] state = board.getBoard();
        assertEquals('r', state[0][2]);
        assertEquals('y', state[1][2]);
    }

    @Test
    void rejectsACounterWhenTheColumnIsFull() {
        Board board = emptyBoard();
        for (int row = 0; row < 6; row++) {
            assertTrue(board.putCounter(0, row % 2 == 0 ? 'r' : 'y'));
        }

        assertFalse(board.putCounter(0, 'r'));
    }

    @Test
    void copiesBoardStateIndependently() {
        Board original = emptyBoard();
        original.putCounter(1, 'r');
        Board copy = original.getCopy();

        copy.putCounter(1, 'y');

        assertEquals('.', original.getBoard()[1][1]);
        assertEquals('y', copy.getBoard()[1][1]);
    }

    private Board emptyBoard() {
        return new Board(".......,.......,.......,.......,.......,.......");
    }
}

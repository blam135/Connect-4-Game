package com.example.connectfour.game;

public enum Cell {
    EMPTY,
    RED,
    YELLOW;

    static Cell fromCoreToken(char token) {
        return switch (token) {
            case '.' -> EMPTY;
            case 'r' -> RED;
            case 'y' -> YELLOW;
            default -> throw new IllegalArgumentException("Unsupported board token: " + token);
        };
    }
}

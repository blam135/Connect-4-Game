package io.github.blam135.connectfour.model;

public enum Cell {
    EMPTY,
    RED,
    YELLOW;

    public static Cell fromCoreToken(char token) {
        return switch (token) {
            case '.' -> EMPTY;
            case 'r' -> RED;
            case 'y' -> YELLOW;
            default -> throw new IllegalArgumentException("Unsupported board token: " + token);
        };
    }
}

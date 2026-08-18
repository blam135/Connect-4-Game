package io.github.blam135.connectfour.model;

public enum PlayerColor {
    RED('r'),
    YELLOW('y');

    private final char coreToken;

    PlayerColor(char coreToken) {
        this.coreToken = coreToken;
    }

    public char coreToken() {
        return coreToken;
    }

    public PlayerColor opponent() {
        return this == RED ? YELLOW : RED;
    }
}

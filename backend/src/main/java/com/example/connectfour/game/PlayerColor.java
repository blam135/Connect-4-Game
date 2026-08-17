package com.example.connectfour.game;

public enum PlayerColor {
    RED('r'),
    YELLOW('y');

    private final char coreToken;

    PlayerColor(char coreToken) {
        this.coreToken = coreToken;
    }

    char coreToken() {
        return coreToken;
    }

    PlayerColor opponent() {
        return this == RED ? YELLOW : RED;
    }
}

package com.example.connectfour.game;

public class GameException extends RuntimeException {

    private final GameErrorCode code;

    GameException(GameErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public GameErrorCode getCode() {
        return code;
    }
}

package com.example.connectfour.game.error;

public class GameException extends RuntimeException {

    private final GameErrorCode code;

    public GameException(GameErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public GameErrorCode getCode() {
        return code;
    }
}

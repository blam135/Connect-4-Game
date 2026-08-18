package io.github.blam135.connectfour.exception;

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

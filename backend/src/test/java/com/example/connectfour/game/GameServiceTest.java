package com.example.connectfour.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.connectfour.core.Board;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameServiceTest {

    private InMemoryGameRegistry registry;
    private GameService service;

    @BeforeEach
    void setUp() {
        registry = new InMemoryGameRegistry();
        service = new GameService(registry);
    }

    @Test
    void startsAnEmptyGameWhenTheHumanMovesFirst() {
        GameSnapshot game = service.startGame(PlayerColor.RED, FirstPlayer.HUMAN);

        assertNotNull(game.gameId());
        assertEquals(GameStatus.IN_PROGRESS, game.status());
        assertEquals(PlayerColor.RED, game.humanColor());
        assertEquals(FirstPlayer.HUMAN, game.firstPlayer());
        assertNull(game.computerColumn());
        assertEquals(42, count(game.board(), Cell.EMPTY));
    }

    @Test
    void makesAnOpeningMoveForEitherComputerColor() {
        for (PlayerColor humanColor : PlayerColor.values()) {
            GameSnapshot game = service.startGame(humanColor, FirstPlayer.COMPUTER);

            assertNotNull(game.computerColumn());
            assertEquals(1, count(game.board(), cellFor(humanColor.opponent())));
            assertEquals(41, count(game.board(), Cell.EMPTY));
            assertEquals(GameStatus.IN_PROGRESS, game.status());
        }
    }

    @Test
    void resumesTheAuthoritativeServerSnapshot() {
        GameSnapshot started = service.startGame(PlayerColor.YELLOW, FirstPlayer.COMPUTER);

        GameSnapshot resumed = service.resumeGame(started.gameId());

        assertEquals(started.gameId(), resumed.gameId());
        assertEquals(started.board(), resumed.board());
        assertEquals(started.status(), resumed.status());
        assertEquals(started.humanColor(), resumed.humanColor());
        assertNull(resumed.computerColumn());
    }

    @Test
    void processesAHumanAndComputerMoveAsOneTurn() {
        GameSnapshot started = service.startGame(PlayerColor.RED, FirstPlayer.HUMAN);

        GameSnapshot game = service.dropCounter(started.gameId(), 3);

        assertEquals(GameStatus.IN_PROGRESS, game.status());
        assertNotNull(game.computerColumn());
        assertEquals(1, count(game.board(), Cell.RED));
        assertEquals(1, count(game.board(), Cell.YELLOW));
    }

    @Test
    void returnsBoardRowsFromTopToBottom() {
        UUID gameId = register(
                PlayerColor.RED,
                GameStatus.IN_PROGRESS,
                "r......",
                "y......");

        GameSnapshot game = service.resumeGame(gameId);

        assertEquals(Cell.EMPTY, game.board().getFirst().getFirst());
        assertEquals(Cell.YELLOW, game.board().get(4).getFirst());
        assertEquals(Cell.RED, game.board().getLast().getFirst());
    }

    @Test
    void abandonsAGame() {
        UUID gameId = service.startGame(PlayerColor.RED, FirstPlayer.HUMAN).gameId();

        service.abandonGame(gameId);

        GameException error = assertThrows(GameException.class, () -> service.resumeGame(gameId));
        assertEquals(GameErrorCode.GAME_NOT_FOUND, error.getCode());
    }

    @Test
    void rejectsInvalidConfigurationAndUnknownGames() {
        GameException configuration = assertThrows(
                GameException.class,
                () -> service.startGame(null, FirstPlayer.HUMAN));
        GameException missing = assertThrows(
                GameException.class,
                () -> service.resumeGame(UUID.randomUUID()));

        assertEquals(GameErrorCode.INVALID_CONFIGURATION, configuration.getCode());
        assertEquals(GameErrorCode.GAME_NOT_FOUND, missing.getCode());
    }

    @Test
    void rejectsColumnsOutsideTheBoard() {
        UUID gameId = service.startGame(PlayerColor.RED, FirstPlayer.HUMAN).gameId();

        GameException below = assertThrows(GameException.class, () -> service.dropCounter(gameId, -1));
        GameException above = assertThrows(GameException.class, () -> service.dropCounter(gameId, 7));

        assertEquals(GameErrorCode.INVALID_COLUMN, below.getCode());
        assertEquals(GameErrorCode.INVALID_COLUMN, above.getCode());
    }

    @Test
    void rejectsAFullColumn() {
        UUID gameId = register(
                PlayerColor.RED,
                GameStatus.IN_PROGRESS,
                "r......",
                "y......",
                "r......",
                "y......",
                "r......",
                "y......");

        GameException error = assertThrows(GameException.class, () -> service.dropCounter(gameId, 0));

        assertEquals(GameErrorCode.COLUMN_FULL, error.getCode());
    }

    @Test
    void rejectsMovesAfterTheGameHasFinished() {
        UUID gameId = register(PlayerColor.YELLOW, GameStatus.HUMAN_WON, "yyyy...");

        GameException error = assertThrows(GameException.class, () -> service.dropCounter(gameId, 4));

        assertEquals(GameErrorCode.GAME_FINISHED, error.getCode());
    }

    @Test
    void recognizesAHumanWinForEitherColorWithoutPlayingAComputerMove() {
        assertHumanWin(PlayerColor.RED, "rrr.yyy");
        assertHumanWin(PlayerColor.YELLOW, "yyy.rrr");
    }

    @Test
    void recognizesAComputerWinForEitherColor() {
        assertComputerWin(PlayerColor.YELLOW, "rrr.y..");
        assertComputerWin(PlayerColor.RED, "yyy.r..");
    }

    @Test
    void recognizesADrawAfterTheHumanFillsTheBoard() {
        UUID gameId = register(
                PlayerColor.RED,
                GameStatus.IN_PROGRESS,
                "rryyrry",
                "yyrryyr",
                "rryyrry",
                "yyrryyr",
                "rryyrry",
                "yyrryy.");

        GameSnapshot game = service.dropCounter(gameId, 6);

        assertEquals(GameStatus.DRAW, game.status());
        assertNull(game.computerColumn());
        assertEquals(0, count(game.board(), Cell.EMPTY));
    }

    private void assertHumanWin(PlayerColor humanColor, String bottomRow) {
        UUID gameId = register(humanColor, GameStatus.IN_PROGRESS, bottomRow);

        GameSnapshot game = service.dropCounter(gameId, 3);

        assertEquals(GameStatus.HUMAN_WON, game.status());
        assertNull(game.computerColumn());
    }

    private void assertComputerWin(PlayerColor humanColor, String bottomRow) {
        UUID gameId = register(humanColor, GameStatus.IN_PROGRESS, bottomRow);

        GameSnapshot game = service.dropCounter(gameId, 6);

        assertEquals(GameStatus.COMPUTER_WON, game.status());
        assertEquals(3, game.computerColumn());
    }

    private UUID register(PlayerColor humanColor, GameStatus status, String... rows) {
        UUID gameId = UUID.randomUUID();
        registry.register(new GameSession(
                gameId,
                board(rows),
                humanColor,
                FirstPlayer.HUMAN,
                status));
        return gameId;
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

    private long count(List<List<Cell>> board, Cell cell) {
        return board.stream().flatMap(List::stream).filter(candidate -> candidate == cell).count();
    }

    private Cell cellFor(PlayerColor color) {
        return color == PlayerColor.RED ? Cell.RED : Cell.YELLOW;
    }
}

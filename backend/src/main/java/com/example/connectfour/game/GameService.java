package com.example.connectfour.game;

import com.example.connectfour.core.Board;
import com.example.connectfour.core.Computer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GameService {

    private static final String EMPTY_BOARD = ".......,.......,.......,.......,.......,.......";
    private static final int COLUMN_COUNT = 7;
    private static final int ROW_COUNT = 6;
    private static final int SEARCH_DEPTH = 4;
    private static final int RED_WIN_SCORE = 10000;
    private static final int YELLOW_WIN_SCORE = -10000;

    private final InMemoryGameRegistry registry;

    public GameService(InMemoryGameRegistry registry) {
        this.registry = registry;
    }

    public GameSnapshot startGame(PlayerColor humanColor, FirstPlayer firstPlayer) {
        if (humanColor == null || firstPlayer == null) {
            throw new GameException(
                    GameErrorCode.INVALID_CONFIGURATION,
                    "Human color and first player are required");
        }

        GameSession session = new GameSession(
                UUID.randomUUID(),
                new Board(EMPTY_BOARD),
                humanColor,
                firstPlayer,
                GameStatus.IN_PROGRESS);

        Integer computerColumn = null;
        if (firstPlayer == FirstPlayer.COMPUTER) {
            computerColumn = playComputerMove(session);
        }

        registry.register(session);
        return snapshot(session, computerColumn);
    }

    public GameSnapshot resumeGame(UUID gameId) {
        GameSession session = requireSession(gameId);
        synchronized (session) {
            return snapshot(session, null);
        }
    }

    public GameSnapshot dropCounter(UUID gameId, int column) {
        GameSession session = requireSession(gameId);
        synchronized (session) {
            ensureGameInProgress(session);
            ensureValidColumn(column);

            if (!session.board().putCounter(column, session.humanColor().coreToken())) {
                throw new GameException(GameErrorCode.COLUMN_FULL, "Column is full: " + column);
            }

            updateStatus(session);
            if (session.status() != GameStatus.IN_PROGRESS) {
                return snapshot(session, null);
            }

            int computerColumn = playComputerMove(session);
            return snapshot(session, computerColumn);
        }
    }

    public void abandonGame(UUID gameId) {
        GameSession session = requireSession(gameId);
        synchronized (session) {
            if (!registry.remove(gameId, session)) {
                throw gameNotFound(gameId);
            }
        }
    }

    private int playComputerMove(GameSession session) {
        PlayerColor computerColor = session.computerColor();
        int[] decision = new Computer().computeColumn(
                true,
                session.board(),
                SEARCH_DEPTH,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                computerColor == PlayerColor.RED);

        int column = decision[0];
        if (column < 0 || !session.board().putCounter(column, computerColor.coreToken())) {
            updateStatus(session);
            throw new IllegalStateException("Computer did not produce a legal move");
        }

        updateStatus(session);
        return column;
    }

    private void updateStatus(GameSession session) {
        int score = new Computer().computeColumn(
                true,
                session.board(),
                0,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                true)[1];

        if (score == winningScore(session.humanColor())) {
            session.setStatus(GameStatus.HUMAN_WON);
        } else if (score == winningScore(session.computerColor())) {
            session.setStatus(GameStatus.COMPUTER_WON);
        } else if (isFull(session.board())) {
            session.setStatus(GameStatus.DRAW);
        } else {
            session.setStatus(GameStatus.IN_PROGRESS);
        }
    }

    private int winningScore(PlayerColor color) {
        return color == PlayerColor.RED ? RED_WIN_SCORE : YELLOW_WIN_SCORE;
    }

    private boolean isFull(Board board) {
        char[][] state = board.getBoard();
        for (int column = 0; column < COLUMN_COUNT; column++) {
            if (state[ROW_COUNT - 1][column] == '.') {
                return false;
            }
        }
        return true;
    }

    private GameSnapshot snapshot(GameSession session, Integer computerColumn) {
        char[][] coreBoard = session.board().getBoard();
        List<List<Cell>> board = new ArrayList<>(ROW_COUNT);

        for (int coreRow = ROW_COUNT - 1; coreRow >= 0; coreRow--) {
            List<Cell> row = new ArrayList<>(COLUMN_COUNT);
            for (int column = 0; column < COLUMN_COUNT; column++) {
                row.add(Cell.fromCoreToken(coreBoard[coreRow][column]));
            }
            board.add(List.copyOf(row));
        }

        return new GameSnapshot(
                session.id(),
                board,
                session.status(),
                session.humanColor(),
                session.firstPlayer(),
                computerColumn);
    }

    private GameSession requireSession(UUID gameId) {
        if (gameId == null) {
            throw gameNotFound(null);
        }
        return registry.find(gameId).orElseThrow(() -> gameNotFound(gameId));
    }

    private void ensureGameInProgress(GameSession session) {
        if (session.status() != GameStatus.IN_PROGRESS) {
            throw new GameException(GameErrorCode.GAME_FINISHED, "Game has already finished");
        }
    }

    private void ensureValidColumn(int column) {
        if (column < 0 || column >= COLUMN_COUNT) {
            throw new GameException(
                    GameErrorCode.INVALID_COLUMN,
                    "Column must be between 0 and " + (COLUMN_COUNT - 1));
        }
    }

    private GameException gameNotFound(UUID gameId) {
        return new GameException(GameErrorCode.GAME_NOT_FOUND, "Game not found: " + gameId);
    }
}

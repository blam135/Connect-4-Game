package com.example.connectfour.service;

import com.example.connectfour.core.Board;
import com.example.connectfour.core.Computer;
import com.example.connectfour.dto.GameAccess;
import com.example.connectfour.dto.GameSnapshot;
import com.example.connectfour.exception.GameErrorCode;
import com.example.connectfour.exception.GameException;
import com.example.connectfour.model.Cell;
import com.example.connectfour.model.FirstPlayer;
import com.example.connectfour.model.GameMode;
import com.example.connectfour.model.GameSession;
import com.example.connectfour.model.GameStatus;
import com.example.connectfour.model.PlayerColor;
import com.example.connectfour.repository.InMemoryGameRepository;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GameService {

    private static final String EMPTY_BOARD = ".......,.......,.......,.......,.......,.......";
    private static final String ROOM_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int ROOM_CODE_LENGTH = 6;
    private static final int COLUMN_COUNT = 7;
    private static final int ROW_COUNT = 6;
    private static final int SEARCH_DEPTH = 4;
    private static final int RED_WIN_SCORE = 10000;
    private static final int YELLOW_WIN_SCORE = -10000;

    private final InMemoryGameRepository repository;
    private final SecureRandom random = new SecureRandom();

    public GameService(InMemoryGameRepository repository) {
        this.repository = repository;
    }

    public GameAccess startGame(PlayerColor humanColor, FirstPlayer firstPlayer) {
        if (humanColor == null || firstPlayer == null) {
            throw new GameException(
                    GameErrorCode.INVALID_CONFIGURATION,
                    "Human color and first player are required");
        }

        String playerToken = newPlayerToken();
        PlayerColor startingColor = firstPlayer == FirstPlayer.HUMAN
                ? humanColor
                : humanColor.opponent();
        GameSession session = GameSession.computer(
                UUID.randomUUID(),
                new Board(EMPTY_BOARD),
                humanColor,
                startingColor,
                playerToken);

        Integer computerColumn = null;
        if (firstPlayer == FirstPlayer.COMPUTER) {
            computerColumn = playComputerMove(session);
        }

        repository.register(session);
        return new GameAccess(playerToken, snapshot(session, humanColor, computerColumn));
    }

    public GameAccess createOnlineGame(PlayerColor hostColor) {
        if (hostColor == null) {
            throw new GameException(GameErrorCode.INVALID_CONFIGURATION, "Host color is required");
        }

        while (true) {
            String playerToken = newPlayerToken();
            GameSession session = GameSession.online(
                    UUID.randomUUID(),
                    new Board(EMPTY_BOARD),
                    hostColor,
                    playerToken,
                    newRoomCode());
            if (repository.registerOnline(session)) {
                return new GameAccess(playerToken, snapshot(session, hostColor, null));
            }
        }
    }

    public GameAccess joinOnlineGame(String suppliedRoomCode) {
        UUID gameId = onlineGameId(suppliedRoomCode);
        return joinOnlineGame(gameId, suppliedRoomCode);
    }

    public UUID onlineGameId(String suppliedRoomCode) {
        String roomCode = normalizeRoomCode(suppliedRoomCode);
        return requireRoom(roomCode).id();
    }

    public GameAccess joinOnlineGame(UUID expectedGameId, String suppliedRoomCode) {
        String roomCode = normalizeRoomCode(suppliedRoomCode);
        GameSession session = requireRoom(roomCode);
        if (!session.id().equals(expectedGameId)) {
            throw roomNotFound(roomCode);
        }

        synchronized (session) {
            ensureRegistered(session);
            if (session.hasBothOnlinePlayers()) {
                throw new GameException(GameErrorCode.ROOM_FULL, "Room is already full");
            }

            PlayerColor guestColor = session.hasPlayer(PlayerColor.RED)
                    ? PlayerColor.YELLOW
                    : PlayerColor.RED;
            String playerToken = newPlayerToken();
            session.addPlayer(guestColor, playerToken);
            session.setStatus(GameStatus.IN_PROGRESS);
            session.setCurrentTurn(PlayerColor.RED);
            return new GameAccess(playerToken, snapshot(session, guestColor, null));
        }
    }

    public GameSnapshot resumeGame(UUID gameId, String playerToken) {
        GameSession session = requireSession(gameId);
        synchronized (session) {
            ensureRegistered(session);
            PlayerColor playerColor = authenticate(session, playerToken);
            return snapshot(session, playerColor, null);
        }
    }

    public GameSnapshot dropCounter(UUID gameId, PlayerColor playerColor, int column) {
        GameSession session = requireSession(gameId);
        synchronized (session) {
            ensureRegistered(session);
            ensurePlayer(session, playerColor);
            ensureValidColumn(column);

            if (session.mode() == GameMode.ONLINE
                    && session.status() == GameStatus.WAITING_FOR_OPPONENT) {
                throw new GameException(GameErrorCode.OPPONENT_OFFLINE, "Opponent is offline");
            }
            ensureGameInProgress(session);
            if (session.mode() == GameMode.ONLINE) {
                ensureOnlineMoveAllowed(session, playerColor);
            }

            if (!session.board().putCounter(column, playerColor.coreToken())) {
                throw new GameException(GameErrorCode.COLUMN_FULL, "Column is full: " + column);
            }

            updateStatus(session);
            if (session.status() != GameStatus.IN_PROGRESS) {
                session.setCurrentTurn(null);
                return snapshot(session, playerColor, null);
            }

            if (session.mode() == GameMode.COMPUTER) {
                int computerColumn = playComputerMove(session);
                return snapshot(session, playerColor, computerColumn);
            }

            session.setCurrentTurn(playerColor.opponent());
            return snapshot(session, playerColor, null);
        }
    }

    public Map<PlayerColor, GameSnapshot> setConnected(
            UUID gameId, PlayerColor playerColor, boolean connected) {
        GameSession session = requireSession(gameId);
        synchronized (session) {
            ensureRegistered(session);
            ensurePlayer(session, playerColor);
            session.setConnected(playerColor, connected);
            return snapshots(session);
        }
    }

    public Map<PlayerColor, GameSnapshot> snapshots(UUID gameId) {
        GameSession session = requireSession(gameId);
        synchronized (session) {
            ensureRegistered(session);
            return snapshots(session);
        }
    }

    public void abandonGame(UUID gameId, PlayerColor playerColor) {
        GameSession session = requireSession(gameId);
        synchronized (session) {
            ensurePlayer(session, playerColor);
            if (!repository.remove(gameId, session)) {
                throw gameNotFound(gameId);
            }
        }
    }

    private void ensureOnlineMoveAllowed(GameSession session, PlayerColor playerColor) {
        if (!session.hasBothOnlinePlayers()
                || !session.isConnected(playerColor)
                || !session.isConnected(playerColor.opponent())) {
            throw new GameException(GameErrorCode.OPPONENT_OFFLINE, "Opponent is offline");
        }
        if (session.currentTurn() != playerColor) {
            throw new GameException(GameErrorCode.NOT_YOUR_TURN, "It is not your turn");
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
        session.setCurrentTurn(session.status() == GameStatus.IN_PROGRESS
                ? session.computerHumanColor()
                : null);
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

        if (score == RED_WIN_SCORE) {
            session.setStatus(GameStatus.RED_WON);
        } else if (score == YELLOW_WIN_SCORE) {
            session.setStatus(GameStatus.YELLOW_WON);
        } else if (isFull(session.board())) {
            session.setStatus(GameStatus.DRAW);
        } else {
            session.setStatus(GameStatus.IN_PROGRESS);
        }
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

    private Map<PlayerColor, GameSnapshot> snapshots(GameSession session) {
        Map<PlayerColor, GameSnapshot> result = new EnumMap<>(PlayerColor.class);
        for (PlayerColor color : PlayerColor.values()) {
            if (session.hasPlayer(color)) {
                result.put(color, snapshot(session, color, null));
            }
        }
        return Map.copyOf(result);
    }

    private GameSnapshot snapshot(
            GameSession session, PlayerColor playerColor, Integer computerColumn) {
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
                session.mode(),
                board,
                session.status(),
                playerColor,
                session.startingColor(),
                session.currentTurn(),
                session.roomCode(),
                session.mode() == GameMode.COMPUTER
                        || (session.hasPlayer(playerColor.opponent())
                                && session.isConnected(playerColor.opponent())),
                computerColumn);
    }

    private GameSession requireSession(UUID gameId) {
        if (gameId == null) {
            throw gameNotFound(null);
        }
        return repository.find(gameId).orElseThrow(() -> gameNotFound(gameId));
    }

    private GameSession requireRoom(String roomCode) {
        return repository.findByRoomCode(roomCode)
                .orElseThrow(() -> roomNotFound(roomCode));
    }

    private void ensureRegistered(GameSession session) {
        if (repository.find(session.id()).orElse(null) != session) {
            throw gameNotFound(session.id());
        }
    }

    private PlayerColor authenticate(GameSession session, String playerToken) {
        PlayerColor playerColor = session.authenticate(playerToken);
        if (playerColor == null) {
            throw new GameException(
                    GameErrorCode.INVALID_PLAYER_TOKEN, "Player token is invalid");
        }
        return playerColor;
    }

    private void ensurePlayer(GameSession session, PlayerColor playerColor) {
        if (playerColor == null || !session.hasPlayer(playerColor)) {
            throw new GameException(
                    GameErrorCode.INVALID_PLAYER_TOKEN, "Player is not part of this game");
        }
    }

    private void ensureGameInProgress(GameSession session) {
        if (session.status() != GameStatus.IN_PROGRESS) {
            throw new GameException(GameErrorCode.GAME_FINISHED, "Game is not in progress");
        }
    }

    private void ensureValidColumn(int column) {
        if (column < 0 || column >= COLUMN_COUNT) {
            throw new GameException(
                    GameErrorCode.INVALID_COLUMN,
                    "Column must be between 0 and " + (COLUMN_COUNT - 1));
        }
    }

    private String normalizeRoomCode(String suppliedRoomCode) {
        if (suppliedRoomCode == null) {
            return "";
        }
        return suppliedRoomCode.trim().toUpperCase();
    }

    private String newPlayerToken() {
        return UUID.randomUUID().toString();
    }

    private String newRoomCode() {
        StringBuilder code = new StringBuilder(ROOM_CODE_LENGTH);
        for (int index = 0; index < ROOM_CODE_LENGTH; index++) {
            code.append(ROOM_ALPHABET.charAt(random.nextInt(ROOM_ALPHABET.length())));
        }
        return code.toString();
    }

    private GameException gameNotFound(UUID gameId) {
        return new GameException(GameErrorCode.GAME_NOT_FOUND, "Game not found: " + gameId);
    }

    private GameException roomNotFound(String roomCode) {
        return new GameException(GameErrorCode.ROOM_NOT_FOUND, "Room not found: " + roomCode);
    }
}

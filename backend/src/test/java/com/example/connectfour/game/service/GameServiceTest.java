package com.example.connectfour.game.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.connectfour.game.error.GameErrorCode;
import com.example.connectfour.game.error.GameException;
import com.example.connectfour.game.model.GameAccess;
import com.example.connectfour.game.model.GameSnapshot;
import com.example.connectfour.game.type.Cell;
import com.example.connectfour.game.type.FirstPlayer;
import com.example.connectfour.game.type.GameMode;
import com.example.connectfour.game.type.GameStatus;
import com.example.connectfour.game.type.PlayerColor;
import com.example.connectfour.core.Board;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
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
    void startsComputerGamesWithPrivateCredentialsAndGeneralizedState() {
        GameAccess access = service.startGame(PlayerColor.RED, FirstPlayer.HUMAN);
        GameSnapshot game = access.game();

        assertNotNull(access.playerToken());
        assertNotNull(game.gameId());
        assertEquals(GameMode.COMPUTER, game.mode());
        assertEquals(GameStatus.IN_PROGRESS, game.status());
        assertEquals(PlayerColor.RED, game.yourColor());
        assertEquals(PlayerColor.RED, game.startingColor());
        assertEquals(PlayerColor.RED, game.currentTurn());
        assertNull(game.roomCode());
        assertTrue(game.opponentConnected());
        assertNull(game.computerColumn());
        assertEquals(42, count(game.board(), Cell.EMPTY));
    }

    @Test
    void preservesTheAtomicComputerOpeningAndReply() {
        GameAccess opening = service.startGame(PlayerColor.YELLOW, FirstPlayer.COMPUTER);

        assertEquals(PlayerColor.RED, opening.game().startingColor());
        assertEquals(PlayerColor.YELLOW, opening.game().currentTurn());
        assertNotNull(opening.game().computerColumn());
        assertEquals(1, count(opening.game().board(), Cell.RED));

        GameSnapshot moved = service.dropCounter(
                opening.game().gameId(), PlayerColor.YELLOW, 3);

        assertNotNull(moved.computerColumn());
        assertEquals(2, count(moved.board(), Cell.RED));
        assertEquals(1, count(moved.board(), Cell.YELLOW));
        assertEquals(PlayerColor.YELLOW, moved.currentTurn());
    }

    @Test
    void supportsComputerOpeningsForEitherColorAssignment() {
        for (PlayerColor humanColor : PlayerColor.values()) {
            GameSnapshot game = service.startGame(humanColor, FirstPlayer.COMPUTER).game();

            assertNotNull(game.computerColumn());
            assertEquals(1, count(game.board(), cellFor(humanColor.opponent())));
            assertEquals(41, count(game.board(), Cell.EMPTY));
            assertEquals(GameStatus.IN_PROGRESS, game.status());
            assertEquals(humanColor, game.currentTurn());
        }
    }

    @Test
    void resumesOnlyWithTheMatchingPrivateToken() {
        GameAccess started = service.startGame(PlayerColor.YELLOW, FirstPlayer.COMPUTER);

        GameSnapshot resumed = service.resumeGame(
                started.game().gameId(), started.playerToken());
        GameException invalid = assertThrows(
                GameException.class,
                () -> service.resumeGame(started.game().gameId(), "wrong-token"));

        assertEquals(started.game().board(), resumed.board());
        assertEquals(PlayerColor.YELLOW, resumed.yourColor());
        assertNull(resumed.computerColumn());
        assertEquals(GameErrorCode.INVALID_PLAYER_TOKEN, invalid.getCode());
    }

    @Test
    void rejectsAResumeWhenTheSessionIsRemovedWhileWaitingForItsLock() throws Exception {
        GameAccess started = service.startGame(PlayerColor.RED, FirstPlayer.HUMAN);
        GameSession session = registry.find(started.game().gameId()).orElseThrow();
        AtomicReference<Throwable> result = new AtomicReference<>();
        Thread resumeThread = Thread.ofPlatform().unstarted(() -> {
            try {
                service.resumeGame(started.game().gameId(), started.playerToken());
            } catch (Throwable exception) {
                result.set(exception);
            }
        });

        synchronized (session) {
            resumeThread.start();
            long deadline = System.nanoTime() + 1_000_000_000L;
            while (resumeThread.getState() != Thread.State.BLOCKED
                    && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertEquals(Thread.State.BLOCKED, resumeThread.getState());
            assertTrue(registry.remove(started.game().gameId(), session));
        }
        resumeThread.join();

        GameException error = (GameException) result.get();
        assertNotNull(error);
        assertEquals(GameErrorCode.GAME_NOT_FOUND, error.getCode());
    }

    @Test
    void recognizesHumanWinsForEitherComputerGameColor() {
        assertComputerGameHumanWin(PlayerColor.RED, "rrr.yyy");
        assertComputerGameHumanWin(PlayerColor.YELLOW, "yyy.rrr");
    }

    @Test
    void recognizesComputerWinsForEitherComputerGameColor() {
        assertComputerWin(PlayerColor.YELLOW, "rrr.y..");
        assertComputerWin(PlayerColor.RED, "yyy.r..");
    }

    @Test
    void recognizesAComputerModeDrawWithoutRequestingAnAiMove() {
        UUID gameId = registerComputer(
                PlayerColor.RED,
                "rryyrry",
                "yyrryyr",
                "rryyrry",
                "yyrryyr",
                "rryyrry",
                "yyrryy.");

        GameSnapshot game = service.dropCounter(gameId, PlayerColor.RED, 6);

        assertEquals(GameStatus.DRAW, game.status());
        assertNull(game.currentTurn());
        assertNull(game.computerColumn());
        assertEquals(0, count(game.board(), Cell.EMPTY));
    }

    @Test
    void createsAndJoinsANormalizedOnlineRoomWithOppositeSeats() {
        GameAccess host = service.createOnlineGame(PlayerColor.YELLOW);

        assertEquals(GameMode.ONLINE, host.game().mode());
        assertEquals(GameStatus.WAITING_FOR_OPPONENT, host.game().status());
        assertEquals(PlayerColor.YELLOW, host.game().yourColor());
        assertEquals(PlayerColor.RED, host.game().startingColor());
        assertEquals(PlayerColor.RED, host.game().currentTurn());
        assertTrue(host.game().roomCode().matches("[A-HJ-NP-Z2-9]{6}"));
        assertFalse(host.game().opponentConnected());

        GameAccess guest = service.joinOnlineGame(
                "  " + host.game().roomCode().toLowerCase() + "  ");

        assertEquals(host.game().gameId(), guest.game().gameId());
        assertEquals(PlayerColor.RED, guest.game().yourColor());
        assertEquals(GameStatus.IN_PROGRESS, guest.game().status());
        assertNotEquals(host.playerToken(), guest.playerToken());

        GameException full = assertThrows(
                GameException.class,
                () -> service.joinOnlineGame(host.game().roomCode()));
        assertEquals(GameErrorCode.ROOM_FULL, full.getCode());
    }

    @Test
    void broadcastsPersonalizedPresenceSnapshots() {
        OnlinePlayers players = onlinePlayers(PlayerColor.RED);

        Map<PlayerColor, GameSnapshot> hostOnline = service.setConnected(
                players.gameId(), players.hostColor(), true);
        assertFalse(hostOnline.get(players.hostColor()).opponentConnected());
        assertTrue(hostOnline.get(players.guestColor()).opponentConnected());

        Map<PlayerColor, GameSnapshot> bothOnline = service.setConnected(
                players.gameId(), players.guestColor(), true);
        assertTrue(bothOnline.get(players.hostColor()).opponentConnected());
        assertTrue(bothOnline.get(players.guestColor()).opponentConnected());

        Map<PlayerColor, GameSnapshot> guestOffline = service.setConnected(
                players.gameId(), players.guestColor(), false);
        assertFalse(guestOffline.get(players.hostColor()).opponentConnected());
    }

    @Test
    void alternatesOnlineTurnsAndRejectsWrongOrOfflinePlayers() {
        GameAccess waitingHost = service.createOnlineGame(PlayerColor.RED);
        GameException waiting = assertThrows(
                GameException.class,
                () -> service.dropCounter(
                        waitingHost.game().gameId(), PlayerColor.RED, 0));
        assertEquals(GameErrorCode.OPPONENT_OFFLINE, waiting.getCode());

        OnlinePlayers players = connectedOnlinePlayers(PlayerColor.RED);
        GameException wrongTurn = assertThrows(
                GameException.class,
                () -> service.dropCounter(players.gameId(), PlayerColor.YELLOW, 0));
        assertEquals(GameErrorCode.NOT_YOUR_TURN, wrongTurn.getCode());

        GameSnapshot redMove = service.dropCounter(players.gameId(), PlayerColor.RED, 0);
        assertEquals(PlayerColor.YELLOW, redMove.currentTurn());
        assertEquals(1, count(redMove.board(), Cell.RED));

        service.setConnected(players.gameId(), PlayerColor.RED, false);
        GameException offline = assertThrows(
                GameException.class,
                () -> service.dropCounter(players.gameId(), PlayerColor.YELLOW, 1));
        assertEquals(GameErrorCode.OPPONENT_OFFLINE, offline.getCode());
    }

    @Test
    void detectsOnlineWinsAndStopsFurtherMoves() {
        OnlinePlayers players = connectedOnlinePlayers(PlayerColor.RED);

        play(players.gameId(), PlayerColor.RED, 0);
        play(players.gameId(), PlayerColor.YELLOW, 0);
        play(players.gameId(), PlayerColor.RED, 1);
        play(players.gameId(), PlayerColor.YELLOW, 1);
        play(players.gameId(), PlayerColor.RED, 2);
        play(players.gameId(), PlayerColor.YELLOW, 2);
        GameSnapshot won = play(players.gameId(), PlayerColor.RED, 3);

        assertEquals(GameStatus.RED_WON, won.status());
        assertNull(won.currentTurn());
        GameException finished = assertThrows(
                GameException.class,
                () -> play(players.gameId(), PlayerColor.YELLOW, 3));
        assertEquals(GameErrorCode.GAME_FINISHED, finished.getCode());
    }

    @Test
    void detectsAnOnlineDraw() {
        OnlinePlayers players = connectedOnlinePlayers(PlayerColor.RED);
        int[] columns = {
            0, 0, 0, 0, 0, 0,
            1, 1, 1, 1, 1, 1,
            4, 2, 2, 2, 2, 2, 2,
            3, 3, 3, 3, 3, 3,
            4, 4, 4, 4, 4,
            5, 5, 5, 5, 5,
            6, 6, 6, 6, 6, 6,
            5
        };

        GameSnapshot game = null;
        for (int move = 0; move < columns.length; move++) {
            PlayerColor color = move % 2 == 0 ? PlayerColor.RED : PlayerColor.YELLOW;
            game = play(players.gameId(), color, columns[move]);
        }

        assertNotNull(game);
        assertEquals(GameStatus.DRAW, game.status());
        assertNull(game.currentTurn());
        assertEquals(0, count(game.board(), Cell.EMPTY));
    }

    @Test
    void serializesConcurrentAttemptsToClaimTheGuestSeat() throws Exception {
        GameAccess host = service.createOnlineGame(PlayerColor.RED);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<GameAccess> first = executor.submit(() -> joinAfterSignal(host, ready, start));
            Future<GameAccess> second = executor.submit(() -> joinAfterSignal(host, ready, start));
            ready.await();
            start.countDown();

            int joined = 0;
            int full = 0;
            for (Future<GameAccess> result : List.of(first, second)) {
                try {
                    result.get();
                    joined++;
                } catch (ExecutionException exception) {
                    GameException error = (GameException) exception.getCause();
                    assertEquals(GameErrorCode.ROOM_FULL, error.getCode());
                    full++;
                }
            }

            assertEquals(1, joined);
            assertEquals(1, full);
        }
    }

    @Test
    void rejectsInvalidAndFullColumns() {
        OnlinePlayers players = connectedOnlinePlayers(PlayerColor.RED);

        GameException invalid = assertThrows(
                GameException.class,
                () -> play(players.gameId(), PlayerColor.RED, 7));
        assertEquals(GameErrorCode.INVALID_COLUMN, invalid.getCode());

        for (int move = 0; move < 6; move++) {
            play(
                    players.gameId(),
                    move % 2 == 0 ? PlayerColor.RED : PlayerColor.YELLOW,
                    0);
        }
        GameException full = assertThrows(
                GameException.class,
                () -> play(players.gameId(), PlayerColor.RED, 0));
        assertEquals(GameErrorCode.COLUMN_FULL, full.getCode());
    }

    @Test
    void abandonmentRemovesBothGameAndRoomIndexes() {
        GameAccess host = service.createOnlineGame(PlayerColor.RED);
        service.abandonGame(host.game().gameId(), PlayerColor.RED);

        GameException gameMissing = assertThrows(
                GameException.class,
                () -> service.resumeGame(host.game().gameId(), host.playerToken()));
        GameException roomMissing = assertThrows(
                GameException.class,
                () -> service.joinOnlineGame(host.game().roomCode()));

        assertEquals(GameErrorCode.GAME_NOT_FOUND, gameMissing.getCode());
        assertEquals(GameErrorCode.ROOM_NOT_FOUND, roomMissing.getCode());
    }

    @Test
    void rejectsInvalidConfigurationAndUnknownRooms() {
        GameException configuration = assertThrows(
                GameException.class,
                () -> service.startGame(null, FirstPlayer.HUMAN));
        GameException missing = assertThrows(
                GameException.class,
                () -> service.joinOnlineGame("ABC123"));

        assertEquals(GameErrorCode.INVALID_CONFIGURATION, configuration.getCode());
        assertEquals(GameErrorCode.ROOM_NOT_FOUND, missing.getCode());
    }

    private OnlinePlayers onlinePlayers(PlayerColor hostColor) {
        GameAccess host = service.createOnlineGame(hostColor);
        GameAccess guest = service.joinOnlineGame(host.game().roomCode());
        return new OnlinePlayers(
                host.game().gameId(), hostColor, guest.game().yourColor());
    }

    private OnlinePlayers connectedOnlinePlayers(PlayerColor hostColor) {
        OnlinePlayers players = onlinePlayers(hostColor);
        service.setConnected(players.gameId(), players.hostColor(), true);
        service.setConnected(players.gameId(), players.guestColor(), true);
        return players;
    }

    private GameSnapshot play(UUID gameId, PlayerColor color, int column) {
        return service.dropCounter(gameId, color, column);
    }

    private void assertComputerGameHumanWin(PlayerColor humanColor, String bottomRow) {
        UUID gameId = registerComputer(humanColor, bottomRow);

        GameSnapshot game = service.dropCounter(gameId, humanColor, 3);

        assertEquals(
                humanColor == PlayerColor.RED ? GameStatus.RED_WON : GameStatus.YELLOW_WON,
                game.status());
        assertNull(game.computerColumn());
    }

    private void assertComputerWin(PlayerColor humanColor, String bottomRow) {
        UUID gameId = registerComputer(humanColor, bottomRow);

        GameSnapshot game = service.dropCounter(gameId, humanColor, 6);

        GameStatus expected = humanColor.opponent() == PlayerColor.RED
                ? GameStatus.RED_WON
                : GameStatus.YELLOW_WON;
        assertEquals(expected, game.status());
        assertEquals(3, game.computerColumn());
    }

    private UUID registerComputer(PlayerColor humanColor, String... rows) {
        UUID gameId = UUID.randomUUID();
        registry.register(GameSession.computer(
                gameId,
                board(rows),
                humanColor,
                humanColor,
                UUID.randomUUID().toString()));
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

    private GameAccess joinAfterSignal(
            GameAccess host, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return service.joinOnlineGame(host.game().roomCode());
    }

    private long count(List<List<Cell>> board, Cell cell) {
        return board.stream().flatMap(List::stream).filter(candidate -> candidate == cell).count();
    }

    private Cell cellFor(PlayerColor color) {
        return color == PlayerColor.RED ? Cell.RED : Cell.YELLOW;
    }

    private record OnlinePlayers(
            UUID gameId, PlayerColor hostColor, PlayerColor guestColor) {}
}

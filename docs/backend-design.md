# Backend design

The Spring Boot backend separates WebSocket transport, game orchestration, and
the preserved Connect Four algorithm. It supports both `COMPUTER` and `ONLINE`
sessions without introducing a database.

For the complete wire contract and runtime flows, see
[Connect Four architecture](architecture.md).

## Package boundaries

```mermaid
flowchart LR
    Boot["io.github.blam135.connectfour<br/>application bootstrap"]
    Config["config<br/>Spring and WebSocket configuration"]
    Transport["websocket<br/>controller-equivalent transport boundary"]
    Service["service<br/>use-case orchestration"]
    Repository["repository<br/>game and room lookup"]
    Model["model<br/>game state and domain values"]
    Dto["dto<br/>immutable boundary records"]
    Exception["exception<br/>typed application failures"]
    Core["core<br/>Board and minimax Computer"]
    Legacy["legacy<br/>terminal runner"]
    Spring["Spring Boot and Jackson"]

    Spring --> Boot
    Boot --> Config
    Config --> Transport
    Transport --> Service
    Transport --> Dto
    Transport --> Model
    Transport --> Exception
    Service --> Repository
    Service --> Model
    Service --> Dto
    Service --> Exception
    Service --> Core
    Repository --> Model
    Model --> Core
    Dto --> Model
    Legacy --> Core
```

This is the familiar controller-service-repository-model shape adapted for a
WebSocket application. The `websocket` package is the controller-equivalent:
unlike an MVC controller, its handler owns a long-lived, bidirectional socket
lifecycle rather than one HTTP request and response. Calling it `controller`
would hide that important distinction.

Dependencies point inward. `websocket` knows about JSON and socket sessions;
`service` coordinates use cases; `repository` owns process-local lookup;
`model` owns mutable game state and domain values; and `dto` defines immutable
values returned across the boundary. None of those inner packages knows about a
browser connection. `core` contains no Spring or transport dependencies, so the
original board and AI remain independently testable.

## Main objects

```mermaid
classDiagram
    class WebSocketConfig
    class GameWebSocketHandler
    class GameConnectionRegistry
    class GameService
    class InMemoryGameRepository
    class GameSession
    class GameAccess
    class GameSnapshot
    class Board
    class Computer

    WebSocketConfig --> GameWebSocketHandler : registers /ws/game
    GameWebSocketHandler --> GameConnectionRegistry : active seat sockets
    GameWebSocketHandler --> GameService : delegates commands
    GameService --> InMemoryGameRepository : sessions and rooms
    InMemoryGameRepository "1" o-- "0..*" GameSession : stores
    GameSession *-- Board : owns
    GameService ..> Computer : evaluates and chooses
    GameService ..> GameAccess : returns new credential
    GameService ..> GameSnapshot : returns state
```

| Object | Responsibility |
| --- | --- |
| `WebSocketConfig` | Registers `/ws/game` and allowed origins |
| `GameWebSocketHandler` | Parses commands, validates connection state, binds seats, broadcasts snapshots, and sends errors |
| `GameConnectionRegistry` | Stores the latest socket for each `(gameId, playerColor)` and supplies striped game locks |
| `GameService` | Creates games, claims rooms, authenticates resumes, applies moves, tracks presence, abandons sessions, and builds snapshots |
| `InMemoryGameRepository` | Maintains UUID-to-session and room-code-to-UUID concurrent maps |
| `GameSession` | Owns one mutable board plus its mode, players, tokens, room, status, turn, and presence |
| `GameAccess` | Returns a private `playerToken` with the first personalized snapshot |
| `GameSnapshot` | Immutable transport-facing game state |
| `Board` and `Computer` | Preserve stacking, position evaluation, and depth-4 minimax search |

Spring constructs the configuration, handler, service, repository, connection
registry, and `JsonMapper` as singleton beans through constructor injection. Each
`GameSession`, `Board`, `GameAccess`, and `GameSnapshot` is created per use case.
`Computer` is created per calculation because it tracks traversal metrics and
must not share mutable search state across games.

## WebSocket connection lifecycle

`WebSocketConfig` participates only during application startup: it registers the
singleton `GameWebSocketHandler` at `/ws/game`. Spring then owns the HTTP upgrade
and calls that handler for messages and close events on every browser
connection.

Each successful upgrade creates a distinct `WebSocketSession`. It begins
**unbound**, with no game ID or player color in its attributes. Its first
successful `START_GAME`, `CREATE_ONLINE_GAME`, `JOIN_ONLINE_GAME`, or
`RESUME_GAME` command establishes that identity. Later commands use the bound
identity instead of accepting a game ID or token from every message.

```mermaid
sequenceDiagram
    participant Browser
    participant Spring as Spring WebSocket runtime
    participant Handler as GameWebSocketHandler
    participant Connections as GameConnectionRegistry
    participant Service as GameService
    participant Repository as InMemoryGameRepository
    participant Game as GameSession and Board
    participant Other as Other online socket

    Browser->>Spring: HTTP Upgrade /ws/game
    Spring-->>Browser: WebSocket opened
    Note over Spring,Handler: New WebSocketSession is initially unbound

    Browser->>Spring: First JSON command
    Spring->>Handler: handleTextMessage(session, message)
    Handler->>Handler: Parse envelope and payload
    Handler->>Service: Start, create, join, or resume
    Service->>Repository: Register or locate game
    Repository-->>Service: GameSession
    Service->>Game: Validate, authenticate, or initialize
    Game-->>Service: Updated state
    Service-->>Handler: GameAccess or GameSnapshot
    Handler->>Connections: Attach game ID, color, and socket
    Handler-->>Browser: GAME_SESSION or GAME_STATE
    opt Online join or resume
        Handler-->>Other: Personalized GAME_STATE
    end

    loop Commands while the socket is bound
        Browser->>Spring: DROP_COUNTER or ABANDON_GAME
        Spring->>Handler: handleTextMessage(session, message)
        Handler->>Connections: Lock game and verify active socket
        Handler->>Service: Execute command with bound game and color
        Service->>Game: Validate and update atomically
        Service-->>Handler: Snapshot or completion
        Handler-->>Browser: Response
        opt Online state change
            Handler-->>Other: Personalized response or broadcast
        end
    end

    Browser-xSpring: Socket closes
    Spring->>Handler: afterConnectionClosed(session)
    Handler->>Connections: Detach only if socket is still active
    Handler->>Service: Mark seat disconnected
    Handler-->>Other: GAME_STATE with opponent offline
```

The handler stores `gameId` and `playerColor` in the Spring session attributes
and stores the socket in `GameConnectionRegistry` under the same pair. Both are
needed: the attributes identify what the socket claims, while the registry
proves it is still the newest authoritative socket for that seat.

### Command-to-class flow

| Command | Handler and connection work | Service, repository, and model work | Result |
| --- | --- | --- | --- |
| `START_GAME` | Require an unbound socket; bind the returned game and human color | Validate configuration, create the computer `GameSession` and `Board`, optionally make the opening AI move, and register by UUID | `GAME_SESSION` to the caller |
| `CREATE_ONLINE_GAME` | Require an unbound socket; lock the new game, attach the host socket, and mark the seat connected | Create the waiting online session, generate token and room code, and atomically reserve both indexes | `GAME_SESSION` to the host |
| `JOIN_ONLINE_GAME` | Resolve the room's game ID, lock it, attach the guest socket, then broadcast to the host | Find the room, atomically claim the remaining color, issue its token, and move the session to `IN_PROGRESS` | `GAME_SESSION` to the guest and `GAME_STATE` to the host |
| `RESUME_GAME` | Require an unbound socket; lock the requested game, attach the authenticated color, and replace that seat's previous socket | Find the session, authenticate `playerToken`, and mark the seat connected | `GAME_STATE` to the caller and, online, an updated state to the opponent |
| `DROP_COUNTER` | Read the bound game ID, lock the game, and reject a socket that is no longer active | Validate membership, status, column, presence, and turn; update the board and status; invoke the AI only in computer mode | Direct `GAME_STATE` for computer play or personalized broadcasts for online play |
| `ABANDON_GAME` | Verify the bound active socket, detach every game socket, and clear their session attributes | Remove the session and its room-code index using the caller's bound identity | `GAME_ABANDONED` with seat-specific reasons |

Malformed envelopes and expected service failures leave this flow through the
handler's exception translation and return an `ERROR` message. Outgoing writes
are synchronized on each `WebSocketSession`, so two broadcasts cannot write to
the same socket concurrently.

The close callback and the safeguards that prevent a replaced socket from
changing presence are expanded in
[Presence, reconnection, and socket replacement](#presence-reconnection-and-socket-replacement).

## Session model

A `GameSession` contains:

- a UUID and `COMPUTER | ONLINE` mode;
- the mutable core `Board`;
- `startingColor`, `currentTurn`, and one of `WAITING_FOR_OPPONENT`,
  `IN_PROGRESS`, `RED_WON`, `YELLOW_WON`, or `DRAW`;
- a token for each occupied player-color seat;
- connection presence for occupied seats;
- an online room code, or the human color for a computer game.

For online creation, the host chooses red or yellow. The room starts in
`WAITING_FOR_OPPONENT`, its `startingColor` and `currentTurn` are red, and only
the host seat exists. The first guest to submit the room code atomically claims
the other color, receives a new token, and changes the status to `IN_PROGRESS`.
Later join attempts receive `ROOM_FULL`.

Computer games have one human seat and no room code. `opponentConnected` is
always true in their snapshots. `startingColor` reflects the chosen first
player, while `currentTurn` represents when the human may submit the next move.

## Repository and game lifecycle

`InMemoryGameRepository` uses two `ConcurrentHashMap` indexes:

```text
game UUID  -> GameSession
room code  -> game UUID
```

Online registration reserves a generated six-character code with
`putIfAbsent`; a collision triggers generation of another code. The alphabet
omits ambiguous characters. Server room lookup trims and uppercases input.

An explicit `ABANDON_GAME` conditionally removes the same session instance from
the UUID index and removes its room-code index entry. The handler then detaches
all current sockets and sends `GAME_ABANDONED`: `YOU_LEFT` to the caller and
`OPPONENT_LEFT` to the other player. An unexpected socket close does not remove
the session; it only updates that seat's presence.

All sessions, room codes, and tokens are process-local. A restart loses them,
and there is no database, account service, matchmaking service, or match
history.

## Command handling

The transport accepts these commands:

- `START_GAME {humanColor, firstPlayer}`
- `CREATE_ONLINE_GAME {hostColor}`
- `JOIN_ONLINE_GAME {roomCode}`
- `RESUME_GAME {gameId, playerToken}`
- `DROP_COUNTER {column}`
- `ABANDON_GAME {}`

Starting or claiming a seat returns `GAME_SESSION {playerToken, game}`. Resuming
authenticates the token against the requested game and returns `GAME_STATE`.
Only the token's color is bound to the new socket; knowing a UUID or room code
alone cannot resume an occupied seat.

`DROP_COUNTER` first checks that the socket is still the active connection for
its bound seat. `GameService` then verifies the seat, column, game status,
online presence, and turn before mutating the board.

For online play, one accepted command places exactly one counter, updates the
result, changes `currentTurn` to the opponent when play continues, and causes
personalized `GAME_STATE` snapshots to be broadcast to both seats. Red always
starts.

For computer play, one accepted human command may place both the human and AI
counters. The AI runs only if the human move was not terminal; the combined
turn is returned as one snapshot with `computerColumn` identifying the AI
counter for the client animation.

## Presence, reconnection, and socket replacement

`GameConnectionRegistry` keys connections by both game UUID and player color.
This lets an online game retain two active sockets. Attaching a new socket for
one seat atomically replaces and closes only that seat's previous socket with
the reason `Game resumed on another connection`.

On a genuine active-socket disconnect, the handler:

1. removes the mapping only if it still points to the closing socket;
2. marks that color disconnected in the session;
3. broadcasts a personalized snapshot to the remaining connection.

The compare-and-remove step prevents a replaced socket's later close callback
from marking the newly resumed seat offline. A valid resume attaches the new
socket, marks the seat connected, returns its state, and broadcasts restored
presence to the opponent.

Presence does not change `GameStatus`. Online controls are effectively paused
because `GameService` rejects a move unless both seats exist and both are
connected. The existing board and `currentTurn` remain available indefinitely
until resume, explicit abandonment, or server restart.

## Concurrency

The handler obtains one of 64 deterministic striped locks for each game UUID.
Within that lock it coordinates connection changes, service calls, broadcasts,
and abandonment. Hash collisions can serialize unrelated games but do not mix
their state.

`GameService` additionally synchronizes on the `GameSession` before reading or
mutating domain state. This makes seat claiming, presence changes, turns,
terminal-state updates, and removal checks atomic for a session. The complete
human-plus-AI turn stays under this monitor. WebSocket writes are synchronized
on the receiving `WebSocketSession`.

These guarantees do not cross process boundaries. Horizontal scaling would
need shared persistence, distributed locking or atomic commands, shared
pub/sub, and connection routing.

## Snapshot boundary

The core board stores row zero at the bottom and uses `.`, `r`, and `y`.
`GameService` reverses rows and maps tokens to `EMPTY`, `RED`, and `YELLOW` for
the API. `GameSnapshot` copies the outer board list and every row, preventing a
caller from modifying server state through a response.

Every online recipient gets a separate snapshot so `yourColor` and
`opponentConnected` are correct for that seat. `currentTurn` becomes null after
a win or draw; `computerColumn` is null outside an AI response.

## Error translation

Expected application failures are `GameException` values with stable codes:

- `GAME_NOT_FOUND`, `ROOM_NOT_FOUND`, `ROOM_FULL`, and
  `INVALID_PLAYER_TOKEN` for lookup and seat access;
- `NOT_YOUR_TURN` and `OPPONENT_OFFLINE` for online coordination;
- `INVALID_CONFIGURATION`, `INVALID_COLUMN`, `COLUMN_FULL`, and
  `GAME_FINISHED` for game validation.

The handler wraps these as `ERROR {code, message, recoverable}`. It also owns
transport codes such as `NO_ACTIVE_GAME`, `CONNECTION_ALREADY_BOUND`, and
`STALE_CONNECTION`. Unexpected exceptions are logged and exposed only as
`INTERNAL_ERROR`.

## Design patterns and intentional limits

- **Layered architecture:** WebSocket delivery depends inward on application
  rules and the core algorithm.
- **Service layer:** `GameService` coordinates use cases without transport
  concerns.
- **Repository:** `InMemoryGameRepository` encapsulates process-local game and
  room lookup. It uses Spring's `@Repository` stereotype, but intentionally has
  no interface while only one storage implementation exists.
- **Connection registry:** `GameConnectionRegistry` separately owns live socket
  identity and per-game locks; it is transport state, not game persistence.
- **Domain aggregate:** `GameSession` groups the board, seats, tokens, turns,
  status, and presence that must change consistently.
- **Immutable DTOs:** records and enums constrain the boundary values.
- **Boundary adapter:** the handler maps JSON to service calls, while snapshot
  conversion isolates the legacy board representation.
- **Exception translation:** typed service failures become stable wire errors.

There is deliberately no repository interface, durable store, event bus, CQRS,
AI strategy interface, or session factory. Those abstractions should be added
only when a second implementation or persistence requirement exists.

## Tests and change locations

| Test area | Coverage |
| --- | --- |
| `BoardTest`, `ComputerTest` | Preserved stacking, copying, evaluation, and minimax behavior |
| `GameServiceTest` | Computer regressions, room lifecycle, seat assignment, turns, wins/draws, presence, tokens, and cleanup |
| `GameWebSocketIntegrationTest` | Two-client broadcasts, authorization, reconnect/replacement, disconnect pause, abandonment, and errors |

| Change | Primary location |
| --- | --- |
| Wire message or connection lifecycle | `websocket/GameWebSocketHandler` |
| Active seat socket or game lock | `websocket/GameConnectionRegistry` |
| Spring properties or endpoint registration | `config` |
| Room, turn, token, presence, or AI orchestration | `service/GameService` |
| Session and room indexes | `repository/InMemoryGameRepository` |
| Per-game mutable state and domain values | `model` |
| Credential and snapshot fields | `dto` and frontend protocol types |
| Stable service errors | `exception` |
| Counter stacking or minimax | `core/Board` and `core/Computer` |

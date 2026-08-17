# Connect Four Revival Plan

## Goal

Rebuild the original terminal Connect Four project as a local full-stack learning application while preserving the existing Java minimax algorithm.

The completed application will have:

- A Java 21 and Spring Boot 4.1 backend.
- A raw JSON WebSocket protocol.
- A React, TypeScript, and Vite frontend.
- Server-owned games that can resume after a browser refresh or temporary disconnect.
- Docker images and a Docker Compose development environment.
- Mermaid architecture documentation under `docs/`.
- A separately scoped cloud deployment phase.

The project is intentionally optimized for learning rather than production completeness.

## Working Agreement

Work is divided into small reviewable batches. For each batch, Codex will:

1. Implement only the requested batch.
2. Run the relevant tests and builds.
3. Report changed files, verification results, and limitations.
4. Leave all changes unstaged and uncommitted.
5. Wait for review and feedback before starting the next batch.

The user will create each commit after reviewing the changes.

## Architecture

### Backend responsibilities

- Treat the backend as the source of truth for each game.
- Keep active games in memory, keyed by a UUID game ID.
- Retain disconnected games until they are abandoned or the server restarts.
- Synchronize commands per game so moves cannot run concurrently.
- Validate commands, columns, game status, and connection ownership.
- Convert the core algorithm's bottom-first board into top-first UI rows.
- Apply the human move and corresponding computer move atomically.
- Keep minimax depth 4 and alpha-beta pruning unchanged.

### Frontend responsibilities

- Let the player choose red or yellow and who moves first.
- Render the setup screen, board, status, loading, and error states.
- Maintain the WebSocket connection through a focused React hook.
- Store the current game ID in browser storage.
- Resume the server-owned game after refresh or temporary disconnection.
- Disable input while disconnected, awaiting the AI, or after completion.

### Game lifetime

- A new game receives a UUID from the backend.
- Refreshing or temporarily losing the socket does not delete the game.
- The latest socket to resume a game becomes its active connection.
- Restarting Spring Boot clears all games.
- `GAME_NOT_FOUND` clears stale browser state and returns the UI to setup.
- Database persistence and cross-device resume are out of scope initially.

## WebSocket Protocol

The client connects to `/ws/game` and exchanges JSON envelopes.

### Client messages

```ts
type ClientMessage =
  | {
      type: "START_GAME";
      payload: {
        humanColor: "RED" | "YELLOW";
        firstPlayer: "HUMAN" | "COMPUTER";
      };
    }
  | {
      type: "RESUME_GAME";
      payload: { gameId: string };
    }
  | {
      type: "DROP_COUNTER";
      payload: { column: number };
    }
  | {
      type: "ABANDON_GAME";
      payload: Record<string, never>;
    };
```

### Server messages

```ts
type Cell = "EMPTY" | "RED" | "YELLOW";

type GameStatus =
  | "IN_PROGRESS"
  | "HUMAN_WON"
  | "COMPUTER_WON"
  | "DRAW";

type ServerMessage =
  | {
      type: "GAME_STATE";
      payload: {
        gameId: string;
        board: Cell[][];
        status: GameStatus;
        humanColor: "RED" | "YELLOW";
        firstPlayer: "HUMAN" | "COMPUTER";
        computerColumn: number | null;
      };
    }
  | {
      type: "GAME_ABANDONED";
      payload: Record<string, never>;
    }
  | {
      type: "ERROR";
      payload: {
        code: string;
        message: string;
        recoverable: boolean;
      };
    };
```

## Implementation Phases

### Phase 1: Clean up the original project

- [x] Remove tracked `.class` files, `Connect4.zip`, and `manifest.txt`.
- [x] Add focused Java, Maven, Node, Vite, IDE, and environment ignore rules.
- [x] Preserve the original Java source and Git history.

Suggested commit:

```text
chore: remove legacy build artifacts
```

### Phase 2: Build the backend server

#### 2.1 Scaffold Spring Boot

- [x] Create a Java 21 Spring Boot 4.1 backend under `backend/`.
- [x] Add Maven Wrapper.
- [x] Add Web MVC, WebSocket, validation, and test dependencies.
- [x] Add a context-loading test.

Suggested commit:

```text
build(backend): scaffold Spring Boot app
```

#### 2.2 Package and characterize the algorithm

- [x] Move `Board` and `Computer` into the backend core package.
- [x] Preserve their contents exactly apart from package declarations.
- [x] Preserve the terminal runner under a legacy package.
- [x] Characterize board stacking, full columns, copies, terminal scores, winning moves, and defensive moves.

Suggested commit:

```text
refactor(core): package game algorithm
```

#### 2.3 Add the game-session application layer

- [x] Add an in-memory concurrent game registry.
- [x] Implement start, move, resume, and abandon operations.
- [x] Add player configuration and game status types.
- [x] Validate illegal columns, full columns, completed games, and invalid commands.
- [x] Convert between core and API board representations.
- [x] Orchestrate the existing AI without changing it.
- [x] Test both colors, both starting players, wins, draws, invalid moves, and resumption.

Suggested commit:

```text
feat(backend): add game session service
```

#### 2.4 Add the WebSocket transport

- [x] Register a raw WebSocket handler at `/ws/game`.
- [x] Implement the agreed JSON message protocol.
- [x] Bind each connection to its current game.
- [x] Make the latest resumed socket authoritative.
- [x] Return structured recoverable and non-recoverable errors.
- [x] Add serialization, malformed-message, reconnect, and integration tests.

Suggested commit:

```text
feat(backend): add WebSocket game protocol
```

### Phase 3: Build the frontend

#### 3.1 Scaffold React

- [x] Create `frontend/` with React, TypeScript, and Vite on Node 24 LTS.
- [x] Add frontend tests and shared protocol types.
- [x] Configure Vite to proxy `/ws` to Spring Boot in development.

Suggested commit:

```text
build(frontend): scaffold React client
```

#### 3.2 Add the WebSocket client

- [x] Add a typed React WebSocket hook.
- [x] Store and resume the current game ID.
- [x] Retry unexpected disconnects with bounded backoff.
- [x] Handle stale games after a backend restart.
- [x] Test connection lifecycle behavior with a mocked WebSocket.

Suggested commit:

```text
feat(frontend): add WebSocket game client
```

#### 3.3 Add the playable UI

- [ ] Add color and starting-player setup.
- [ ] Build an accessible responsive 7x6 board.
- [ ] Add status, loading, terminal, and error states.
- [ ] Add New Game and reconnect behavior.
- [ ] Test the complete browser game flow.

Suggested commit:

```text
feat(frontend): add playable game UI
```

### Phase 4: Containerize and document

#### 4.1 Containerize the application

- [ ] Add a multi-stage Java backend image.
- [ ] Add a multi-stage Node and Nginx frontend image.
- [ ] Configure Nginx to serve React and proxy WebSocket upgrades.
- [ ] Add focused Docker ignore files and Docker Compose.
- [ ] Verify a complete game through the containerized frontend.

Suggested commit:

```text
build(docker): containerize application
```

#### 4.2 Add architecture documentation

- [ ] Create `docs/architecture.md`.
- [ ] Add Mermaid system, component, gameplay, reconnection, lifecycle, and container diagrams.
- [ ] Document state ownership, concurrency, board conversion, and failure behavior.
- [ ] Document native and Docker startup commands.
- [ ] Link the architecture document from the README.

Suggested commit:

```text
docs: document application architecture
```

### Phase 5: Scope and deploy to the cloud

Cloud implementation will not begin until the architecture is reviewed and the following decisions are made:

- Cloud provider and monthly cost limit.
- Combined or separate frontend and backend services.
- Container registry and deployment mechanism.
- Domain, TLS termination, and WebSocket proxy support.
- Environment configuration and secret handling.
- Manual deployment or CI/CD.
- Logging and health monitoring.
- Single backend instance, sticky routing, or shared persistence.

The in-memory game registry assumes one backend instance. Multiple replicas require sticky routing or shared state.

Suggested scoping commit:

```text
docs(cloud): define deployment architecture
```

Suggested implementation commit after approval:

```text
build(cloud): add deployment configuration
```

## Verification and Completion Criteria

- All Java and frontend tests pass.
- The algorithm method bodies remain unchanged.
- A complete game works in native development and Docker Compose.
- Refreshing or reconnecting restores the server-owned game.
- Restarting Spring Boot fails gracefully and returns the player to setup.
- Architecture diagrams match the implemented components and protocol.
- README instructions work from a clean checkout.

The local learning iteration is complete after Phases 1 through 4. Phase 5 is a separate deployment exercise.

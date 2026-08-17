# Connect Four Bruno collection

This collection exercises the raw JSON WebSocket endpoint at `/ws/game`.

## Use it

1. Start the Spring Boot backend from the repository's `backend/` directory:

   ```bash
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
   ./mvnw spring-boot:run
   ```

2. Open Bruno and choose **Open Collection**.
3. Select this `bruno/` directory.
4. Select the **Local** environment.
5. Open **Start game**, connect, and send its message.
6. Copy `payload.gameId` from the `GAME_STATE` response when you need to resume
   the game from another request.

The VS Code extension displays one WebSocket message per `.bru` request, so the
game-session payloads are split into **Start game**, **Drop counter**, **Invalid
column**, and **Abandon game**. Each request opens its own WebSocket connection.
The latter three payloads therefore cannot continue the connection created by
**Start game** when invoked as standalone requests; the server responds with
`NO_ACTIVE_GAME`.

To test reconnection, copy `payload.gameId` from a `GAME_STATE` response into
the Local environment's `gameId` variable. Keep **Start game** connected,
then open and connect **Resume game**. Sending its first message transfers the
game to the new socket and closes the original one.

The **Protocol errors** request contains malformed and invalid messages for
checking the server's structured error responses.

## Variables

| Variable | Default | Purpose |
| --- | --- | --- |
| `wsUrl` | `ws://localhost:8080/ws/game` | WebSocket server endpoint |
| `gameId` | Placeholder | ID copied from a `GAME_STATE` response |
| `humanColor` | `RED` | `RED` or `YELLOW` |
| `firstPlayer` | `HUMAN` | `HUMAN` or `COMPUTER` |
| `column` | `3` | Zero-indexed board column from `0` to `6` |

WebSocket requests are interactive in Bruno and are not included in its normal
collection runner.

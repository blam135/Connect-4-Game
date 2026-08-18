# Connect Four

A full-stack Connect Four game with two ways to play:

- challenge the preserved depth-4 minimax computer; or
- create an online room and play live with another person in a second browser.

The React client communicates with a Spring Boot server through a raw JSON
WebSocket protocol. The backend is authoritative for the board, turns, player
seats, connection presence, and results.

## Run locally

Prerequisites: Java 21 and Node.js 24 or newer.

The backend `Makefile` provides shortcuts for its common development commands:

```bash
cd backend
make help
```

Start the backend:

```bash
cd backend
make run
```

Start the frontend in another terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/ws/game` to Spring Boot on port
`8080`.

To test online play:

1. In one browser, choose **Play online**, **Create a room**, and a color.
2. Copy the invite link or note the six-character room code.
3. Open the link in a different browser, browser profile, or private window so
   each player has separate `localStorage`, then join the room.
4. Red moves first. Closing either connection pauses play until that player
   reconnects; choosing **Leave game** ends the room for both players.

Alternatively, run both applications with Docker:

```bash
docker compose up --build
```

Then open `http://localhost:3000` in two separate browser storage contexts.

Run the preserved terminal game independently of Spring Boot with:

```bash
cd backend
make cli
```

## Architecture and limitations

Rooms, boards, player tokens, and connection presence live only in the single
backend process. There is no database, account system, matchmaking, chat, or
durable match history. Games disappear when the service sleeps, restarts, or
is redeployed.

Documentation:

- [Application architecture](docs/architecture.md) — protocol, state ownership,
  gameplay, reconnection, failures, and local testing.
- [Backend design](docs/backend-design.md) — game sessions, room registry,
  concurrency, WebSocket connections, and minimax integration.
- [Frontend design](docs/frontend-design.md) — UI modes, hook lifecycle,
  session storage, input policy, accessibility, and tests.
- [Spring configuration](docs/spring-configuration.md) — ports, origins, and
  environment variables.
- [Cloud deployment](docs/cloud-deployment.md) — production image construction
  and Render runtime behavior.

## Deploy to Render

The Render Blueprint in `infra/render.yaml` deploys the frontend and backend as
one Web Service. Spring serves the built React assets and `/ws/game` from the
same public origin. Free services can sleep after inactivity, so the first
request may be delayed and every in-memory game is lost when the process stops.

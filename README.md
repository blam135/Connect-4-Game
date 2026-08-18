# Connect Four

A local full-stack Connect Four game with a React client, a raw WebSocket
protocol, and a Spring Boot backend that preserves the original Java minimax
opponent.

Documentation:

- [Application architecture](docs/architecture.md) — system diagrams, state
  ownership, failure behavior, and startup instructions.
- [Backend structure and object-oriented design](docs/backend-design.md) —
  class dependencies, Spring object construction, and design patterns.
- [Frontend structure and React design](docs/frontend-design.md) — React and
  DOM concepts, component data flow, state, hooks, styling, and testing.
- [Cloud deployment](docs/cloud-deployment.md) — production image construction,
  Render runtime behavior, configuration, and request routing.

## Deploy to Render

> [!NOTE]
> The files in `infra/` are intended for cloud deployment.
> For local development with separate frontend and backend containers, use
> `compose.yaml` instead.

The repository includes a Render Blueprint that deploys the frontend and
backend together as one free Web Service. The production Docker image builds
the React client, packages it into Spring Boot, and serves the website and
WebSocket endpoint from the same public URL.

Render will provide an HTTPS `onrender.com` URL and will redeploy whenever a
commit is pushed to the connected branch. Free services sleep after periods of
inactivity, so the first visit after a sleep can take longer to load. Active
games are stored in memory and are lost whenever the service sleeps or restarts.

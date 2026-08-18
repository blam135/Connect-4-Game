# Spring Boot configuration and environment variables

TypeScript itself does not load `.env` files. A runtime or build tool such as
Node.js or Vite loads them. Spring Boot uses a similar idea through its
externalized configuration system, but it does **not** automatically load a
project-root `.env` file.

## Configuration in this application

Default values and property definitions live in
`backend/src/main/resources/application.properties`:

```properties
server.port=${PORT:8080}
connectfour.websocket.allowed-origin-patterns=${WEBSOCKET_ALLOWED_ORIGIN_PATTERNS:http://localhost:*,http://127.0.0.1:*,https://*.onrender.com}
```

The `${NAME:default}` syntax means:

1. use the environment variable named `NAME` when it is present;
2. otherwise, use the value after the colon.

For example, `PORT=9000` makes Spring listen on port `9000`; without `PORT`, it
uses `8080`. Render provides `PORT` to the deployed service automatically.

Spring loads `application.properties` from the classpath when the application
starts. It can also load profile-specific files such as
`application-local.properties` or `application-test.properties` when the
corresponding profile is active.

## How values reach Java classes

The application's settings are grouped in `ConnectFourProperties`, a typed
configuration class with the prefix `connectfour`:

```java
@ConfigurationProperties("connectfour")
public record ConnectFourProperties(WebSocket websocket) {
    public record WebSocket(List<String> allowedOriginPatterns) {}
}
```

Spring binds the property
`connectfour.websocket.allowed-origin-patterns` to
`websocket.allowedOriginPatterns()`. `WebSocketConfig` receives this object
through constructor injection, just like any other dependency. Feature classes
therefore do not need to look up string property names themselves.

Spring also supports relaxed binding. A Spring property such as
`connectfour.websocket.allowed-origin-patterns` can be supplied directly as an
environment variable named
`CONNECTFOUR_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS`. This application additionally
uses the shorter explicit alias `WEBSOCKET_ALLOWED_ORIGIN_PATTERNS` in
`application.properties`.

## Using `.env` locally

Spring Boot does not read `.env` by default. A `.env` file only affects the
application when another tool reads it and exports or passes its values to the
Java process. Common options are:

- Docker Compose with an `env_file` entry;
- environment variables configured in the IDE run configuration;
- variables exported in the shell before starting Spring;
- a local Spring profile file that is excluded from Git.

For production, configure values in Render's environment settings rather than
committing a `.env` file or secrets to the repository.

## Spring backend versus Vite frontend

There is an important security difference:

- Spring environment variables are read by the server process and remain on
  the server unless the application deliberately returns them.
- Vite variables exposed through `import.meta.env` are compiled into the
  browser bundle. Variables prefixed with `VITE_` are public and must never
  contain secrets.

Use Spring or Render environment variables for server-only values and secrets.
Use Vite environment variables only for non-secret browser configuration.

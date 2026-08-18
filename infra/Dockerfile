# syntax=docker/dockerfile:1

# Cloud deployment image: builds the React frontend and packages it into the
# Spring Boot application so Render can run the full stack as one web service.
# Build this file with the repository root as its context. For the separate
# local containers, use compose.yaml instead.

FROM node:24-alpine AS frontend-build

WORKDIR /workspace/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk-alpine AS backend-build

WORKDIR /workspace/backend

COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw

COPY backend/src/ src/
COPY --from=frontend-build /workspace/frontend/dist/ src/main/resources/static/
RUN --mount=type=cache,target=/root/.m2 ./mvnw --batch-mode package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S connectfour && adduser -S connectfour -G connectfour

WORKDIR /app

COPY --from=backend-build --chown=connectfour:connectfour \
    /workspace/backend/target/connect-four-backend-*.jar app.jar

USER connectfour

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]

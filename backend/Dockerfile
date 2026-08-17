# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline

COPY src/ src/
RUN ./mvnw package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S connectfour && adduser -S connectfour -G connectfour

WORKDIR /app

COPY --from=build --chown=connectfour:connectfour /workspace/target/connect-four-backend-*.jar app.jar

USER connectfour

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

# syntax=docker/dockerfile:1

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Resolve dependencies first so source edits do not re-download the world.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
    && cp target/*.jar app.jar

# ---------- runtime ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# DotenvEnvironmentPostProcessor refuses to start without /app/.env and runs before
# profiles resolve. An empty one satisfies it; real values come from compose env vars.
RUN groupadd --system cvgen && useradd --system --gid cvgen --home /app cvgen \
    && mkdir -p /app/storage && touch /app/.env && chown -R cvgen:cvgen /app

COPY --from=build --chown=cvgen:cvgen /workspace/app.jar app.jar

USER cvgen
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.awt.headless=true"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

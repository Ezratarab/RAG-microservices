# syntax=docker/dockerfile:1
# One Dockerfile for every service. Build from the repo root so the shared "common" module is included:
#   docker build --build-arg SERVICE=rag-service -t rag-service .

# =========================
# BUILD STAGE
# =========================
FROM maven:3.9.9-eclipse-temurin-17 AS build
ARG SERVICE
WORKDIR /src

# All module poms first (Maven needs the whole reactor to resolve -pl/-am)
COPY pom.xml .
COPY common/pom.xml common/
COPY auth-service/pom.xml auth-service/
COPY billing-service/pom.xml billing-service/
COPY rag-service/pom.xml rag-service/
COPY gateway-service/pom.xml gateway-service/

COPY common/src common/src
COPY ${SERVICE}/src ${SERVICE}/src

# The cache mount keeps downloaded dependencies between builds.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl ${SERVICE} -am package -DskipTests

# =========================
# RUNTIME STAGE
# =========================
FROM eclipse-temurin:17-jre
ARG SERVICE

# curl for the compose health checks; a non-root user to run the app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --no-create-home app \
    && mkdir -p /data/uploads && chown -R app /data

WORKDIR /app
COPY --from=build /src/${SERVICE}/target/app.jar app.jar

USER app

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

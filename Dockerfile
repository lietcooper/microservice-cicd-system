# ============================================================
# Build stage — compiles all modules
# ============================================================
FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

# Copy Gradle wrapper and build configuration first (layer caching)
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./
COPY config/ config/

# Create subproject dirs and copy their build files
RUN mkdir -p common cli server worker
COPY common/build.gradle common/
COPY cli/build.gradle cli/
COPY server/build.gradle server/
COPY worker/build.gradle worker/

# Pre-download dependencies (cached unless build files change)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon 2>/dev/null || true

# Copy source code
COPY common/ common/
COPY cli/ cli/
COPY server/ server/
COPY worker/ worker/

# Build all JARs (skip tests and checkstyle for speed)
RUN ./gradlew assemble --no-daemon

# ============================================================
# Server target
# ============================================================
FROM eclipse-temurin:17-jre AS server

RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/server/build/libs/server-0.1.0.jar app.jar
RUN chown -R 1000:1000 /app

USER 1000

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# ============================================================
# Worker target
# ============================================================
FROM eclipse-temurin:17-jre AS worker

RUN mkdir -p /tmp/cicd-workspaces && chown -R 1000:1000 /tmp/cicd-workspaces

WORKDIR /app
COPY --from=build /app/worker/build/libs/worker-0.1.0.jar app.jar
RUN chown -R 1000:1000 /app

USER 1000

EXPOSE 8081
ENTRYPOINT ["java", "-Djava.io.tmpdir=/tmp/cicd-workspaces", "-jar", "app.jar"]

# ============================================================
# CLI target
# ============================================================
FROM eclipse-temurin:17-jre AS cli

RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*

RUN git config --system --add safe.directory /workspace

WORKDIR /workspace
COPY --from=build /app/cli/build/libs/cli-0.1.0.jar /app/cli.jar
RUN chown -R 1000:1000 /app

USER 1000
ENTRYPOINT ["java", "-jar", "/app/cli.jar"]

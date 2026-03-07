# CICD System

Custom CI/CD system with:
- local YAML validation and dry-run
- server-side pipeline execution
- RabbitMQ-based orchestration
- worker-side Docker job execution
- PostgreSQL-backed run reports

## Modules

- `cli`: `cicd` command
- `common`: shared models, parser, validator, messaging
- `server`: REST API, Git snapshotting, report persistence
- `worker`: pipeline orchestration and Docker execution

## Infrastructure

Start PostgreSQL and RabbitMQ:

```bash
docker compose up -d
```

Run the server:

```bash
./gradlew :server:bootRun
```

Run the worker:

```bash
./gradlew :worker:bootRun
```

## CLI

Build the CLI:

```bash
./gradlew :cli:jar
```

Run locally through Gradle:

```bash
./gradlew :cli:run --args="verify .pipelines/default.yaml"
```

Or run the jar:

```bash
java -jar cli/build/libs/cli-0.1.0.jar verify .pipelines/default.yaml
```

## Commands

Verify a pipeline file:

```bash
./gradlew :cli:run --args="verify .pipelines/default.yaml"
```

Preview execution order:

```bash
./gradlew :cli:run --args="dryrun .pipelines/default.yaml"
```

Run a pipeline from a repository URL:

```bash
./gradlew :cli:run --args="run --repo-url https://github.com/example/repo.git --name default"
```

Run a specific branch / commit:

```bash
./gradlew :cli:run --args="run --repo-url https://github.com/example/repo.git --name default --branch main --commit <sha>"
```

Query reports:

```bash
./gradlew :cli:run --args="report --pipeline default"
./gradlew :cli:run --args="report --pipeline default --run 1"
./gradlew :cli:run --args="report --pipeline default --run 1 --stage build"
./gradlew :cli:run --args="report --pipeline default --run 1 --stage build --job compile"
```

## Execution Model

`run` is asynchronous:

1. CLI sends `repoUrl`, `branch`, `commit`, and pipeline selection to the server
2. Server clones the repo into a temporary snapshot
3. Server validates the pipeline from the committed snapshot
4. Server archives the snapshot and publishes a pipeline message to RabbitMQ
5. Worker extracts the archive, orchestrates stages/jobs, and runs jobs in Docker
6. Worker sends status updates back to the server
7. CLI uses `report` to inspect progress and results

## Test

```bash
./gradlew test
```

## Coverage Report

```bash
./gradlew jacocoTestReport
```

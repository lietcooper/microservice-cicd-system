# Developer Installation & Usage Guide

This guide is for developers who want to clone, build, run, and test the CI/CD system locally.

## Prerequisites

Install the following tools on Ubuntu 24.04 or a comparable Linux environment:

- Git
- Java 17
- Docker
- Docker Compose

Optional but recommended:
- `curl`
- `psql`

## 1. Clone the Repository

```bash
git clone https://github.com/CS7580-SEA-SP26/d-team.git
cd d-team
```

## 2. Verify Local Tooling

```bash
git --version
java -version
docker --version
docker compose version
```

Expected result:
- Git is available
- Java 17 is installed
- Docker is installed and running
- Docker Compose is available

## 3. Start Local Infrastructure

Developer mode uses PostgreSQL and RabbitMQ from `docker-compose.yaml`.

```bash
docker compose up -d
```

Verify:

```bash
docker compose ps
```

Expected result:
- `cicd-postgres` is running
- `cicd-rabbitmq` is running

## 4. Build the Project

```bash
./gradlew build
```

This builds all modules:
- `common`
- `cli`
- `server`
- `worker`

## 5. Run the Server

In one terminal:

```bash
./gradlew :server:bootRun
```

Expected result:
- Spring Boot starts successfully
- the server listens on port `8080`

## 6. Run the Worker

In a second terminal:

```bash
./gradlew :worker:bootRun
```

Expected result:
- the worker starts successfully
- the worker connects to RabbitMQ

## 7. Run the CLI

You can run the CLI directly through Gradle:

```bash
./gradlew :cli:run --args="--help"
```

Or build the CLI jar:

```bash
./gradlew :cli:jar
java -jar cli/build/libs/cli-0.1.0.jar --help
```

## 8. Local Validation Commands

Validate a known-good pipeline:

```bash
./gradlew :cli:run --args="verify .pipelines/default.yaml"
```

Preview execution order:

```bash
./gradlew :cli:run --args="dryrun .pipelines/default.yaml"
```

Validation failure examples:

```bash
./gradlew :cli:run --args="verify .pipelines/invalid-cycle.yaml"
./gradlew :cli:run --args="verify .pipelines/invalid-empty-stage.yaml"
./gradlew :cli:run --args="verify .pipelines/invalid-type.yaml"
```

## 9. Run Example Pipelines

Successful pipeline example:

```bash
./gradlew :cli:run --args="run --repo-url https://github.com/lietcooper/success-demo-repo.git --name default --branch main"
```

Use the run number printed by the command in the report examples below.

Failed pipeline example:

```bash
./gradlew :cli:run --args="run --repo-url https://github.com/lietcooper/fail-demo-repo.git --name default --branch main"
```

## 10. Query Reports

Replace `<RUN_NO>` with the run number printed by `cicd run`.

Level 1: all runs for a pipeline:

```bash
./gradlew :cli:run --args="report --pipeline default"
```

Level 2: a specific run:

```bash
./gradlew :cli:run --args="report --pipeline default --run <RUN_NO>"
```

Level 3: a specific stage:

```bash
./gradlew :cli:run --args="report --pipeline default --run <RUN_NO> --stage build"
```

Level 4: a specific job:

```bash
./gradlew :cli:run --args="report --pipeline default --run <RUN_NO> --stage build --job compile"
```

## 11. Verify Successful Installation

For the full checklist, see [system-health-check.md](/Users/lijunwan/Documents/NEU/cs7580/assignments/d-team/system-health-check.md).

Recommended developer verification steps:

```bash
docker compose up -d
./gradlew :server:bootRun
./gradlew :worker:bootRun
./gradlew :cli:run --args="verify .pipelines/default.yaml"
./gradlew :cli:run --args="dryrun .pipelines/default.yaml"
./gradlew :cli:run --args="run --repo-url https://github.com/lietcooper/success-demo-repo.git --name default --branch main"
./gradlew :cli:run --args="report --pipeline default"
```

Expected result:
- PostgreSQL and RabbitMQ are running
- server starts on port `8080`
- worker connects to RabbitMQ
- CLI commands work
- the successful example pipeline is persisted and visible through `report`

## 12. Test Commands

Run all tests:

```bash
./gradlew test
```

Run a specific module test task:

```bash
./gradlew :common:test
./gradlew :cli:test
./gradlew :server:test
./gradlew :worker:test
```

Generate coverage reports:

```bash
./gradlew jacocoTestReport
```

## 13. Useful Logs and Diagnostics

Check infrastructure containers:

```bash
docker compose ps
docker compose logs rabbitmq
docker compose logs postgres
```

If running server and worker through Gradle, inspect the terminal output directly.

If running server and worker as containers in evaluator mode:

```bash
docker compose -f docker-compose.evaluator.yaml logs server
docker compose -f docker-compose.evaluator.yaml logs worker
```

## 14. Stop the System

Stop only infrastructure:

```bash
docker compose down
```

Stop and remove infrastructure volumes:

```bash
docker compose down -v
```

If `server` or `worker` are running through Gradle, stop them with `Ctrl+C` in their terminals.

## 15. Common Developer Issues

| Problem | Solution |
|---------|----------|
| `./gradlew` fails because Java is missing | Install Java 17 and verify with `java -version` |
| PostgreSQL or RabbitMQ is not starting | Run `docker compose logs postgres` or `docker compose logs rabbitmq` |
| `run` stays queued forever | Verify the worker is running and connected to RabbitMQ |
| `run` fails immediately | Check the repository URL, branch, commit, and pipeline name |
| Docker job execution fails | Verify Docker daemon access and inspect worker logs |
| `report` returns no runs | Check server logs and database connectivity |

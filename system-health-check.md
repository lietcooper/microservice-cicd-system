# System Health Check

This document explains how to verify that all CI/CD system components have been installed successfully and are working together correctly.

## Prerequisites

- Docker is installed and running
- Docker Compose is available
- The evaluator system has been started with:

```bash
docker compose -f docker-compose.evaluator.yaml up -d
```

- The CLI launcher script has been installed or is available as `./cicd`

## 1. Verify Docker

Check that Docker and Docker Compose are installed correctly:

```bash
docker --version
docker compose version
```

Expected result:
- both commands succeed
- version information is printed

## 2. Verify All Containers Are Running

```bash
docker compose -f docker-compose.evaluator.yaml ps
```

Expected result:
- `cicd-postgres` is running
- `cicd-rabbitmq` is running
- `cicd-server` is running
- `cicd-worker` is running

If health checks are shown, `postgres` and `rabbitmq` should be healthy.

## 3. Verify the CLI

Check that the CLI launcher works:

```bash
cicd --help
```

If the CLI was not installed into `PATH`, run:

```bash
./cicd --help
```

Expected result:
- the CLI help text is displayed
- no Docker or image pull error is shown

## 4. Verify the Server

Check the server logs:

```bash
docker compose -f docker-compose.evaluator.yaml logs server
```

Expected result:
- Spring Boot starts successfully
- no repeated startup failure messages appear
- the service is listening on port `8080`

Optional HTTP check:

```bash
curl http://localhost:8080/pipelines/default/runs
```

Expected result:
- the server responds
- the response may be empty or return not found if no runs exist yet

## 5. Verify the Worker

Check the worker logs:

```bash
docker compose -f docker-compose.evaluator.yaml logs worker
```

Expected result:
- the worker starts successfully
- the worker connects to RabbitMQ
- no repeated connection failure or crash loop appears

## 6. Verify RabbitMQ

Check container state:

```bash
docker compose -f docker-compose.evaluator.yaml ps rabbitmq
```

Optional UI check:
- URL: `http://localhost:15672`
- Username: `cicd`
- Password: `cicd`

Expected result:
- RabbitMQ management UI is reachable
- queues and exchanges can be inspected after pipeline activity

## 7. Verify PostgreSQL

Check PostgreSQL readiness:

```bash
docker exec cicd-postgres pg_isready -U cicd
```

Expected result:
- PostgreSQL reports that it is accepting connections

## 8. End-to-End Smoke Test

Run a known successful pipeline:

```bash
cicd run --repo-url https://github.com/lietcooper/success-demo-repo.git --name default --branch main
```

Expected result:
- the CLI reports that the pipeline run has been queued
- a pipeline name and run number are printed

Then query the report:

```bash
cicd report --pipeline default
```

Expected result:
- at least one run is listed for pipeline `default`
- the most recent run eventually becomes `SUCCESS`

You can also inspect more detail:

```bash
cicd report --pipeline default --run 1
cicd report --pipeline default --run 1 --stage build
cicd report --pipeline default --run 1 --stage build --job compile
```

## 9. Failure Smoke Test

Run a known failing pipeline:

```bash
cicd run --repo-url https://github.com/lietcooper/fail-demo-repo.git --name default --branch main
```

Then query the report:

```bash
cicd report --pipeline default
```

Expected result:
- a new run appears
- the run eventually becomes `FAILED`

## 10. What a Healthy System Looks Like

A healthy installation should satisfy all of the following:
- Docker works
- all evaluator containers are running
- the CLI help command works
- the server responds to HTTP requests
- the worker stays connected to RabbitMQ
- PostgreSQL accepts connections
- a successful example pipeline can complete
- a failing example pipeline is reported correctly

## 11. Common Failure Indicators

| Symptom | Likely Cause |
|---------|--------------|
| `cicd --help` fails | CLI image cannot be pulled, Docker is not running, or registry login is missing |
| `server` exits repeatedly | database or RabbitMQ connection problem |
| `worker` exits repeatedly | RabbitMQ connection problem or Docker socket issue |
| `run` never progresses | worker not running or RabbitMQ problem |
| `run` fails immediately | repository URL, branch, commit, or pipeline name is invalid |
| `report` shows no runs | the run request was not accepted or persisted |

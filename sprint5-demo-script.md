# Demo Script

## Demo Setup

[//]: # (Replace these two values after you push the example repositories to GitHub:)

[//]: # ()
[//]: # (```bash)

[//]: # (export SUCCESS_REPO_URL="https://github.com/<org-or-user>/success-repo.git")

[//]: # (export FAIL_REPO_URL="https://github.com/<org-or-user>/fail-repo.git")
[//]: # (```)

## 1. Start Infrastructure

```bash
docker compose up -d
```

## 2. Start Server

```bash
./gradlew :server:bootRun
```

## 3. Start Worker

```bash
./gradlew :worker:bootRun
```

## 4. Show Local Validation

```bash
./gradlew :cli:run --args="verify .pipelines/default.yaml"
./gradlew :cli:run --args="dryrun .pipelines/default.yaml"
./gradlew :cli:run --args="verify .pipelines/invalid-cycle.yaml"
```

## 5. Show Successful Pipeline Run

```bash
./gradlew :cli:run --args="run --repo-url https://github.com/lietcooper/sucess-demo-repo --name default --branch main"
./gradlew :cli:run --args="report --pipeline default"
./gradlew :cli:run --args="report --pipeline default --run 1"
./gradlew :cli:run --args="report --pipeline default --run 1 --stage build"
./gradlew :cli:run --args="report --pipeline default --run 1 --stage build --job compile"
```

## 6. Show Failed Pipeline Run

```bash
./gradlew :cli:run --args="run --repo-url https://github.com/lietcooper/fail-demo-repo --name default --branch main"
./gradlew :cli:run --args="report --pipeline default --run 2"
./gradlew :cli:run --args="report --pipeline default --run 2 --stage test"
./gradlew :cli:run --args="report --pipeline default --run 2 --stage test --job tests"
```

## 7. Talking Points

- `verify` and `dryrun` are local-only CLI features
- `run` is asynchronous and returns a queued pipeline run
- server clones committed code from `repoUrl`
- server archives the snapshot and sends it through RabbitMQ
- worker extracts the archive and runs jobs in Docker
- `report` reads persisted pipeline, stage, and job state from PostgreSQL

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

Developer mode starts only PostgreSQL and RabbitMQ:

```bash
docker compose up -d
```

For evaluator / non-developer setup, use [sprint5-non-developer-guide.md](/Users/lijunwan/Documents/NEU/cs7580/assignments/d-team/sprint5-non-developer-guide.md) and `docker-compose.evaluator.yaml`.

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

## Example Repositories

After you push the local demo repos under `demo-repos/` to GitHub, set these two URLs:

```bash
export SUCCESS_REPO_URL="https://github.com/<org-or-user>/success-repo.git"
export FAIL_REPO_URL="https://github.com/<org-or-user>/fail-repo.git"
```

## Evaluator Walkthrough

Validation failure example:

```bash
./gradlew :cli:run --args="verify .pipelines/invalid-cycle.yaml"
```

Successful pipeline example:

```bash
./gradlew :cli:run --args="run --repo-url $SUCCESS_REPO_URL --name default --branch main"
./gradlew :cli:run --args="report --pipeline default"
./gradlew :cli:run --args="report --pipeline default --run 1"
./gradlew :cli:run --args="report --pipeline default --run 1 --stage build"
./gradlew :cli:run --args="report --pipeline default --run 1 --stage build --job compile"
```

Failed pipeline example:

```bash
./gradlew :cli:run --args="run --repo-url $FAIL_REPO_URL --name default --branch main"
./gradlew :cli:run --args="report --pipeline default --run 2"
./gradlew :cli:run --args="report --pipeline default --run 2 --stage test"
./gradlew :cli:run --args="report --pipeline default --run 2 --stage test --job tests"
```

The local example repository sources are:
- `demo-repos/success-repo`
- `demo-repos/fail-repo`

## Observability

The system includes a full observability stack deployed alongside the application via Helm.

### Stack

| Component | Purpose | Port |
|-----------|---------|------|
| OpenTelemetry Collector | Central telemetry ingestion (receives OTLP, fans out to backends) | 4317 (gRPC), 4318 (HTTP) |
| Prometheus | Metrics storage, scrapes `/actuator/prometheus` from server and worker | 9090 |
| Loki | Log aggregation (receives structured JSON logs via OTel Collector) | 3100 |
| Tempo | Distributed trace storage (receives spans via OTel Collector) | 3200 |
| Grafana | Visualization (dashboards, log viewer, trace explorer) | 3000 |

### Metrics

Five custom metrics are exposed via Micrometer on the `/actuator/prometheus` endpoint:

| Metric | Type | Labels |
|--------|------|--------|
| `cicd_pipeline_runs_total` | Counter | pipeline, status |
| `cicd_pipeline_duration_seconds` | Timer | pipeline |
| `cicd_stage_duration_seconds` | Timer | pipeline, stage |
| `cicd_job_duration_seconds` | Timer | pipeline, stage, job |
| `cicd_job_runs_total` | Counter | pipeline, stage, job, status |

### Tracing

Distributed traces follow the span hierarchy `pipeline > stage > job`. Trace context propagates via W3C TraceContext headers injected into RabbitMQ message headers. Each pipeline run stores its `trace_id` in PostgreSQL for linking from dashboards and the report CLI.

### Grafana Dashboards

Four dashboards are provisioned as code (JSON ConfigMaps):

1. **Pipeline Overview** &mdash; total runs by status, duration over time, recent runs table (PostgreSQL). Clicking a trace-id links to the Trace Explorer.
2. **Stage & Job Breakdown** &mdash; per-stage and per-job duration and status for a selected pipeline.
3. **Logs Viewer** &mdash; filter logs by pipeline, run number, stage, job, and source (system vs. container).
4. **Trace Explorer** &mdash; full span hierarchy for a given pipeline run via Tempo.

### Accessing Grafana

After deploying to Kubernetes:

```bash
kubectl port-forward svc/cicd-cicd-grafana 3000:3000 -n cicd
```

Open http://localhost:3000 (default credentials: `admin` / `admin`).

Other useful port-forwards:

```bash
kubectl port-forward svc/cicd-cicd-prometheus 9090:9090 -n cicd
kubectl port-forward svc/cicd-cicd-tempo 3200:3200 -n cicd
kubectl port-forward svc/cicd-cicd-loki 3100:3100 -n cicd
```

### Kubernetes Deployment

```bash
# Build and push images (from project root, linux/amd64 for EKS)
docker buildx build --platform linux/amd64 --target server -t yskigpg/cicd-server:latest -f Dockerfile --push .
docker buildx build --platform linux/amd64 --target worker -t yskigpg/cicd-worker:latest -f Dockerfile --push .

# Deploy via Helm
helm upgrade --install cicd ./helm/cicd -n cicd --create-namespace

# Validate the deployment
./validate-observability.sh
```

### Prometheus Alert Rules

Three alert rules are pre-configured:

| Alert | Severity | Condition |
|-------|----------|-----------|
| `CicdPipelineConsecutiveFailures` | critical | 3+ pipeline failures in 30 min with no successes |
| `CicdJobDurationHigh` | warning | Job p95 duration > 10 minutes |
| `CicdPipelineDurationHigh` | warning | Pipeline p95 duration > 30 minutes |

## Test

```bash
./gradlew test
```

## Coverage Report

```bash
./gradlew jacocoTestReport
```

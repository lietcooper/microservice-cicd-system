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

## Prerequisites

- Java 17 (JDK)
- Docker
- For Kubernetes deployment: [minikube](https://minikube.sigs.k8s.io/) + [Helm 3](https://helm.sh/docs/intro/install/)

## Quick Start (Kubernetes — recommended)

Build images, deploy, and run a pipeline:

```bash
# 1. Build Docker images
docker build --target server -t cicd-server:latest .
docker build --target worker -t cicd-worker:latest .

# 2. Load into minikube
minikube image load cicd-server:latest
minikube image load cicd-worker:latest

# 3. Deploy via Helm (use local images, not registry)
helm upgrade --install cicd ./helm/cicd -n cicd --create-namespace \
  --set server.image.repository=cicd-server \
  --set server.image.pullPolicy=IfNotPresent \
  --set worker.image.repository=cicd-worker \
  --set worker.image.pullPolicy=IfNotPresent

# 4. Wait for pods
kubectl get pods -n cicd   # all should be Running

# 5. Port-forward the server
kubectl port-forward svc/cicd-cicd-server 8080:8080 -n cicd &

# 6. Build and use the CLI
./gradlew :cli:jar
java -jar cli/build/libs/cli-0.1.0.jar verify .pipelines/demo-success.yaml
java -jar cli/build/libs/cli-0.1.0.jar run \
  --file .pipelines/demo-success.yaml \
  --repo-url https://github.com/lietcooper/fail-demo-repo \
  --server http://localhost:8080
```

For the full deployment guide see [K8S-SETUP.md](K8S-SETUP.md). For post-deploy validation see [system-health-check.md](system-health-check.md).

## Infrastructure (Local Dev Mode)

Developer mode starts only PostgreSQL and RabbitMQ:

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

Or run the jar directly:

```bash
java -jar cli/build/libs/cli-0.1.0.jar verify .pipelines/default.yaml
```

## Documentation

| Document | Description |
|----------|-------------|
| [CLI Reference](dev-docs/cli-reference.md) | Full list of commands, options, exit codes, and examples |
| [API Reference](dev-docs/api-reference.md) | Server REST API endpoints, inputs, outputs, and errors |
| [System Architecture](dev-docs/design/system-architecture-diagrams.md) | End-to-end execution flow, message topology, database schema, wave execution |
| [Kubernetes Deployment](K8S-SETUP.md) | Helm chart deployment guide with configuration reference |
| [System Health Check](system-health-check.md) | Kubernetes and Helm health checks, smoke tests, and troubleshooting |
| [Feature Status](FeatureStatus.md) | Complete list of implemented, partial, and planned features |

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

The demo pipelines use this public repository as the git source:

```bash
# Contains simple shell scripts that demo-success.yaml, failure-true.yaml, etc. reference
export REPO_URL="https://github.com/lietcooper/fail-demo-repo"
```

## Evaluator Walkthrough

All examples below assume the server is running (either via K8s port-forward or local `bootRun`) and the CLI jar is built (`./gradlew :cli:jar`). Alias for convenience:

```bash
alias cicd='java -jar cli/build/libs/cli-0.1.0.jar'
```

Validation failure example:

```bash
cicd verify .pipelines/invalid-cycle.yaml
```

Successful pipeline (with artifacts):

```bash
cicd run --file .pipelines/demo-success.yaml \
  --repo-url https://github.com/lietcooper/fail-demo-repo \
  --server http://localhost:8080
# wait ~10s, then:
cicd report --pipeline demo-success --run 1 --server http://localhost:8080
cicd report --pipeline demo-success --run 1 --stage build --server http://localhost:8080
```

Parallel execution proof:

```bash
cicd run --file .pipelines/parallel-test.yaml \
  --repo-url https://github.com/lietcooper/fail-demo-repo \
  --server http://localhost:8080
# wait ~25s, then check that all 4 jobs share the same start time:
cicd report --pipeline parallel-test --run 1 --stage run --server http://localhost:8080
```

Allow-failures:

```bash
cicd run --file .pipelines/failure-true.yaml \
  --repo-url https://github.com/lietcooper/fail-demo-repo \
  --server http://localhost:8080
# wait ~30s, then:
cicd report --pipeline failure-true --run 1 --stage test --server http://localhost:8080
# linter shows failures: true + FAILED, but pipeline is SUCCESS
```

Expected failure:

```bash
cicd run --file .pipelines/failure-false.yaml \
  --repo-url https://github.com/lietcooper/fail-demo-repo \
  --server http://localhost:8080
# pipeline becomes FAILED (expected)
cicd report --pipeline failure-false --run 1 --server http://localhost:8080
```

## Kubernetes Deployment

All system components can be deployed to Kubernetes via the Helm chart in `helm/cicd/`. See [K8S-SETUP.md](K8S-SETUP.md) for the deployment guide, then use [system-health-check.md](system-health-check.md) to verify the installation.

### Component K8s-Enablement

| Component | K8s Enabled | Requirement | Notes |
|-----------|:-----------:|:-----------:|-------|
| Server | Yes | **Required** | Stateless REST API |
| Worker | Yes | **Required** | Stateless job executor + Docker-in-Docker |
| PostgreSQL | Yes | Optional | Stateful; can use external instance (`postgresql.enabled: false`) |
| RabbitMQ | Yes | Optional | Stateful; can use external instance (`rabbitmq.enabled: false`) |
| Observability stack | Yes | Optional | OTel Collector, Prometheus, Loki, Tempo, Grafana |
| CLI | No | N/A | Runs on developer machine, connects to server via HTTP |

Quick deploy (after building and loading images into minikube):

```bash
helm upgrade --install cicd ./helm/cicd -n cicd --create-namespace \
  --set server.image.repository=cicd-server \
  --set server.image.pullPolicy=IfNotPresent \
  --set worker.image.repository=cicd-worker \
  --set worker.image.pullPolicy=IfNotPresent
kubectl port-forward svc/cicd-cicd-server 8080:8080 -n cicd
```

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
# Build images locally
docker build --target server -t cicd-server:latest .
docker build --target worker -t cicd-worker:latest .

# Load into minikube
minikube image load cicd-server:latest
minikube image load cicd-worker:latest

# Deploy via Helm (point to local images)
helm upgrade --install cicd ./helm/cicd -n cicd --create-namespace \
  --set server.image.repository=cicd-server \
  --set server.image.pullPolicy=IfNotPresent \
  --set worker.image.repository=cicd-worker \
  --set worker.image.pullPolicy=IfNotPresent
```

### Prometheus Alert Rules

Three alert rules are pre-configured:

| Alert | Severity | Condition |
|-------|----------|-----------|
| `CicdPipelineConsecutiveFailures` | critical | 3+ pipeline failures in 30 min with no successes |
| `CicdJobDurationHigh` | warning | Job p95 duration > 10 minutes |
| `CicdPipelineDurationHigh` | warning | Pipeline p95 duration > 30 minutes |

## Test & Reports

### Running Locally

Run all tests:

```bash
./gradlew test
```

Generate aggregated test coverage report (all modules combined):

```bash
./gradlew jacocoAggregatedReport
```

Output: `build/reports/jacoco/aggregated/html/index.html`

Generate static analysis (Checkstyle) reports:

```bash
./gradlew checkstyleMain checkstyleTest
```

Output: `<module>/build/reports/checkstyle/main.html` for each module.

Generate Javadoc:

```bash
./gradlew javadoc
```

Output: `<module>/build/docs/javadoc/index.html` for each module.

### On GitHub Actions

Three CI/CD workflows run automatically and upload reports as downloadable artifacts:

| Workflow | Trigger | Artifacts Uploaded |
|----------|---------|-------------------|
| **PR Review** (`pr.yml`) | Pull request to `main` | test-results, coverage-report, checkstyle-reports |
| **Main** (`main.yml`) | Push to `main` | test-results, coverage-report, checkstyle-reports, javadoc |
| **Release** (`release.yml`) | Tag push (`v*`) | test-results, coverage-report, checkstyle-reports, javadoc |

To download: go to the **Actions** tab on GitHub, click a workflow run, and scroll to the **Artifacts** section at the bottom of the summary page.

The Main and Release workflows also build Docker images for server, worker, and cli.

> **Note:** Because this repository is private under the `CS7580-SEA-SP26` organization, the GitHub Actions `GITHUB_TOKEN` does not have permission to push images to `ghcr.io`. The workflows build the images to verify they compile correctly, but do not push them to a registry. To build and use the images locally:
>
> ```bash
> docker build --target server -t cicd-server:latest .
> docker build --target worker -t cicd-worker:latest .
> docker build --target cli -t cicd-cli:latest .
> ```

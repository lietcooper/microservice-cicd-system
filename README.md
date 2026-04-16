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

For the current full deployment path, use the Helm chart in `helm/cicd/` and the Kubernetes health-check guide in [system-health-check.md](system-health-check.md).

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

Quick deploy:

```bash
helm upgrade --install cicd ./helm/cicd -n cicd --create-namespace
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

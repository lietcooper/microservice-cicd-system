# Feature Status

## Fully Implemented

### Core Pipeline Engine

- **Pipeline YAML Parsing & Validation** — Parses pipeline definitions including stages, jobs, images, scripts, dependencies (`needs`), artifacts, and `allow-failure`. Reports errors with line/column positions. Detects dependency cycles and validates stage assignments.
- **Execution Planner** — Topological sort (Kahn's algorithm) groups jobs into dependency waves. Stages run sequentially; jobs within each wave run in parallel.
- **Allow-Failure Jobs** — Jobs marked `allow-failure` don't block the pipeline. Tracked through execution and persisted in the database.

### CLI

- `verify` — Validate pipeline YAML syntax and semantics
- `dryrun` — Preview execution order and wave grouping
- `run` — Submit a pipeline to the server for execution
- `report` — Query run history at four levels: pipeline, run, stage, job
- `status` — Check current pipeline run status by repo or file

### Server & API

- **Pipeline Execution** — `POST /api/pipelines/execute` accepts a pipeline name or inline YAML, clones the repo, and kicks off execution.
- **Report Endpoints** — Four-level drill-down: all runs for a pipeline, a specific run with stages, a stage with jobs, and individual job details (including artifacts and trace ID).
- **Status Endpoints** — Query by repository URL or pipeline name.

### Docker

- Jobs run inside Docker containers with the repo mounted at `/workspace`. Handles image pulls (300s timeout), script execution (600s timeout), log streaming, exit code capture, and container cleanup.

### Persistence

- PostgreSQL with Flyway migrations (4 versions). Stores pipeline runs, stage runs, job runs, and artifact metadata. Spring Data JPA with auto-incrementing run numbers and cascade relationships. Status tracked at every phase boundary (start/end of pipeline, stage, job).

### Messaging

- RabbitMQ-based choreography. Message types: pipeline-execute, job-execute, job-result, status-update, status-event. Trace context propagated through W3C headers on AMQP messages.

### Artifacts

- Collects build artifacts using glob patterns (including `**` wildcards). Uploads to MinIO (S3-compatible) with keys like `{pipeline}/{run}/{stage}/{job}/{path}`. Metadata persisted in the artifacts table and surfaced in job-level reports.

### Observability

- **Tracing** — OpenTelemetry spans form a pipeline → stage → job hierarchy. Trace IDs stored in PostgreSQL and included in report responses. Context propagates through RabbitMQ.
- **Metrics** — Five Prometheus metrics covering pipeline/stage/job durations and run counts, exposed at `/actuator/prometheus`.
- **Logging** — Structured JSON logs (Logstash encoder) with MDC fields for pipeline, run number, stage, job, and source. Container output streamed through SLF4J.
- **Dashboards** — Four Grafana dashboards: Pipeline Overview, Stage & Job Breakdown, Logs Viewer, and Trace Explorer. Datasources provisioned automatically.
- **Alerting** — Three Prometheus alert rules: consecutive pipeline failures (critical), high job duration, and high pipeline duration.

### Kubernetes Deployment

- Helm chart packages the full stack: server, worker (with Docker-in-Docker sidecar), PostgreSQL, RabbitMQ, MinIO, OpenTelemetry Collector, Prometheus, Loki, Tempo, and Grafana. Includes health probes, service definitions, ConfigMaps, and value profiles for Minikube and cloud.

### Git Integration

- Clones repos, checks out branches/commits, captures commit hashes, archives workspaces for distributed execution, and discovers pipeline files (`.cicd.yaml`).

### Testing

- 52 test files across all modules. Uses H2 in-memory database and MockDockerRunner so tests run without external dependencies. Covers parsing, validation, execution planning, services, controllers, messaging, metrics, and CLI commands.

---

## Partly Implemented

### Artifact Download

Artifacts are collected, stored in MinIO, and tracked in the database — but there's no REST endpoint to actually download them. The CLI report shows storage locations but can't retrieve the files.

### Log Forwarding to Loki

Structured JSON logs are produced with the right MDC fields, and the OTel Collector config includes a Loki exporter. However, the end-to-end path (stdout → OTel Collector → Loki) hasn't been validated, and there are no retention or cardinality policies in place.

### Worker Concurrency Control

Jobs are grouped into waves and dispatched through RabbitMQ, but there's no configurable concurrency limit per worker and no backpressure mechanism if a worker gets overloaded.

### Report Filtering & Pagination

The four-level report hierarchy works, but there's no way to filter by date range or status across runs, no pagination for large result sets, and no aggregate statistics (success rates, average durations).

### Secrets Management

The Helm chart has a `secret.yaml` template and references credentials for RabbitMQ and MinIO, but pipelines can't inject secrets into job containers. No vault integration or environment-variable secret mechanism exists.

### Pipeline Retry & Recovery

Failed jobs are recorded and `allow-failure` works correctly, but there's no way to retry a failed job or resume a failed pipeline. No retry counts, backoff strategies, or manual intervention points.

### Webhook Triggers

The server can clone any repo and accept branch/commit parameters, but there's no webhook listener. Pipelines must be triggered manually via the `run` command — no auto-trigger on push or PR events.

### Pipeline Templates & Reuse

The YAML parser is solid, but there's no support for template variables, pipeline inheritance, composition, or parameterized pipelines.

### Build Caching

Docker images are pulled and workspaces are mounted, but there's no caching strategy between runs — no layer caching, no artifact caching, and no cache invalidation rules.

### Multi-Repository Support

The server accepts arbitrary repo URLs, but a single pipeline can't orchestrate across multiple repos. No monorepo path-based triggers or cross-repo dependency management.
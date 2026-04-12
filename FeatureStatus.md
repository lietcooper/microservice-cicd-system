# Feature Status

## Fully Implemented

1. **YAML Pipeline Parsing & Validation** -- parse pipeline/stages/jobs from YAML with line/column error reporting, cycle detection, dependency validation, type checking
2. **CLI Commands** -- 5 subcommands: `verify`, `dryrun`, `run`, `report`, `status`
3. **REST API** -- `POST /api/pipelines/execute`, 4-level report endpoints (`/pipelines/{name}/runs/...`), status endpoints
4. **Docker Integration** -- job execution in containers, image pulling with timeout, workspace mounting at `/workspace`, real-time log streaming, exit code capture, cleanup
5. **Database Persistence** -- PostgreSQL via Flyway (4 migrations), entities for pipeline/stage/job runs + artifacts, Spring Data JPA repositories, run-number auto-increment
6. **RabbitMQ Messaging** -- queue-based choreography with pipeline-execute, job-execute, job-result, status-update, and status-event messages; trace context propagation via W3C headers
7. **Wave-Based Parallel Execution** -- Kahn's algorithm topological sort, jobs grouped into dependency waves, sequential stages with parallel jobs within waves
8. **Artifacts Support** -- glob pattern matching (exact, wildcard, `**`), MinIO/S3 upload, DB metadata persistence, report integration at Level 4
9. **OpenTelemetry Tracing** -- span hierarchy (pipeline->stage->job), trace-id stored in DB, context propagation through RabbitMQ, root span in server, child spans in worker
10. **Prometheus Metrics** -- 5 metrics: pipeline runs total, pipeline/stage/job duration, job runs total; exposed via `/actuator/prometheus`
11. **Structured Logging** -- Logback JSON encoder (Logstash format), MDC fields (pipeline, run_no, stage, job, source), SLF4J throughout
12. **Grafana Dashboards** -- 4 dashboards: Pipeline Overview, Stage & Job Breakdown, Logs Viewer, Trace Explorer; datasource provisioning
13. **Prometheus Alerting** -- 3 alert rules: consecutive failures (critical), high job duration, high pipeline duration
14. **Kubernetes & Helm Deployment** -- server, worker (with DinD sidecar), PostgreSQL, RabbitMQ, MinIO, OTel Collector, Prometheus, Loki, Tempo, Grafana; health probes, configurable values
15. **Git Integration** -- repo cloning/snapshotting, branch/commit selection, hash capture, workspace archiving, pipeline discovery (`.cicd.yaml`)
16. **Allow-Failure Jobs** -- optional jobs that don't block pipeline on failure, tracked in DB and execution logic
17. **Test Suite** -- 52 test files across all modules using H2 in-memory DB, MockDockerRunner, covering parsing, validation, execution, services, controllers, messaging, metrics, CLI

## Partly Implemented

1. **Artifact Download** -- artifacts are collected, stored in MinIO, and metadata is persisted; **missing**: no REST endpoint to download/retrieve artifact files, no content-type serving, CLI shows locations but can't fetch files
2. **Log Forwarding to Loki** -- JSON logs with MDC fields, OTel Collector config has Loki exporter, Loki deployed via Helm; **missing**: log shipping path (stdout -> OTel -> Loki) not validated end-to-end, no retention policies
3. **Worker Concurrency Control** -- wave-based grouping exists, job dispatch via RabbitMQ; **missing**: no configurable concurrency limit, no backpressure mechanism
4. **Report Filtering** -- 4-level hierarchical reports with trace-id; **missing**: no date range filtering, no status filtering across runs, no pagination, no aggregate statistics
5. **Secrets Management** -- Helm `secret.yaml` template exists, RabbitMQ/MinIO credentials referenced; **missing**: no secret injection into job containers, no vault integration, no env-var secrets for pipelines
6. **Pipeline Retry / Recovery** -- jobs track failure status, allow-failure flag works; **missing**: no retry mechanism, no configurable retry count/backoff, no resume of failed pipelines
7. **Git Webhook Integration** -- repo cloning and branch/commit specification work; **missing**: no webhook listener, no auto-trigger on push/PR, manual `run` command required
8. **Pipeline Templates / Reuse** -- YAML parsing is comprehensive; **missing**: no variable substitution, no pipeline inheritance/composition, no parameterized pipelines
9. **Build Caching** -- Docker images and workspace mounting functional; **missing**: no layer caching strategy, no artifact caching between runs, no cache invalidation
10. **Multi-Repository Support** -- server can clone arbitrary repo URLs; **missing**: no multi-repo orchestration in one pipeline, no monorepo path-based triggers
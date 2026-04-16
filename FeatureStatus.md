# Feature Status

This document summarizes the current functionality of the project for submission.
It reflects the implemented system in the repository as of 2026-04-14.

## System Overview

The project implements a remote-service CI/CD architecture:

- the `cicd` CLI runs on the developer machine
- pipeline execution is submitted to a server
- the server validates requests, snapshots repositories, and queues work
- workers execute jobs inside Docker containers
- execution state and reports are persisted in PostgreSQL
- RabbitMQ is used for orchestration between services

In addition to the base project requirements, the system also includes artifact storage, observability tooling, and Kubernetes deployment assets.

## Implemented Functionalities

### 1. Pipeline Configuration

The system supports repository-local pipeline configuration in YAML.

- Pipeline files are stored under `.pipelines/`
- Supported core fields:
  - `pipeline.name`
  - `pipeline.description`
  - `stages`
  - job `stage`
  - job `image`
  - job `script`
  - job `needs`
- Default stages are applied when `stages` is omitted:
  - `build`
  - `test`
  - `docs`

Validation currently enforces:

- pipeline name is required
- at least one stage must exist
- no stage may be empty
- each job must define a valid stage
- each job must define an image
- each job must define a script
- `needs` must be non-empty if specified
- each dependency in `needs` must exist
- dependencies must remain within the same stage
- dependency cycles inside a stage are rejected
- duplicate pipeline names are detected when verifying a directory

Error reporting includes file, line, and column information.

### 2. CLI Commands

The CLI currently provides the following commands:

#### `verify`

- validates a single YAML file
- validates all YAML files in a directory
- performs syntax and semantic validation

#### `dryrun`

- validates a pipeline file
- prints the execution order of stages and jobs

#### `run`

- accepts `--name` or `--file`
- supports optional `--branch` and `--commit`
- submits execution to the server
- supports remote execution flow through server and worker services

#### `report`

- supports four report levels:
  - pipeline
  - run
  - stage
  - job
- displays run metadata, statuses, timestamps, git information, and artifact metadata

#### `status`

- reports current status by repository URL
- reports status for a specific run
- shows stage and job status hierarchy

### 3. Execution Engine

The execution engine includes:

- stage-ordered pipeline execution
- topological ordering of jobs within a stage
- dependency-wave grouping for parallel job execution
- stage-by-stage orchestration in the worker
- dependency-aware skipping when required jobs fail

### 4. Server And API

The server provides:

- pipeline execution endpoint:
  - `POST /api/pipelines/execute`
- report endpoints:
  - `GET /pipelines/{name}/runs`
  - `GET /pipelines/{name}/runs/{runNo}`
  - `GET /pipelines/{name}/runs/{runNo}/stages/{stageName}`
  - `GET /pipelines/{name}/runs/{runNo}/stages/{stageName}/jobs/{jobName}`
- status endpoints:
  - `GET /pipelines/status?repo=...`
  - `GET /pipelines/{name}/status?run=...`

The execution endpoint supports:

- pipeline lookup by name
- inline pipeline YAML submission
- repository URL input
- optional branch and commit selection
- repository snapshot creation
- validation before execution is queued

### 5. Git Integration

The system currently supports:

- cloning a repository to a temporary snapshot
- checking out an optional branch
- checking out an optional commit
- validating branch existence
- validating commit existence
- validating that a commit belongs to the requested branch
- recording actual branch and commit in run metadata
- archiving workspaces for worker execution

### 6. Docker-Based Job Execution

Jobs are executed in Docker containers with:

- workspace mounted at `/workspace`
- image pull before execution
- 300-second image pull timeout
- 600-second job execution timeout
- real-time log streaming
- exit-code capture
- best-effort container cleanup

### 7. Reporting And Persistence

The system persists execution data in PostgreSQL and supports historical reporting.

Persisted records include:

- pipeline runs
- stage runs
- job runs
- artifact metadata

Tracked metadata includes:

- run number
- status
- start time
- end time
- git repository
- git branch
- git commit hash
- trace ID

Flyway migrations are included for schema management.

### 8. Messaging

RabbitMQ is used for service-to-service coordination.

Implemented message flow includes:

- pipeline execution messages
- job execution messages
- job result messages
- status update messages
- status event messages

The worker uses correlation IDs to coordinate parallel job waves.

### 9. Artifact Support

The system includes artifact collection and storage.

- Artifact patterns are defined in pipeline YAML
- glob matching supports `*` and `**`
- exact files and directories are supported
- matched artifacts are uploaded to MinIO
- artifact metadata is stored and returned in job reports

### 10. Observability

The system includes observability support for tracing, metrics, and logs.

Implemented features include:

- OpenTelemetry tracing in server and worker
- pipeline, stage, and job span hierarchy
- trace ID persistence in pipeline runs
- Prometheus metrics exposure through Spring Actuator
- structured JSON logs with MDC fields
- OpenTelemetry log appender configuration

### 11. Kubernetes Deployment

The repository includes a Helm chart for deploying the full system.

Charted components include:

- server
- worker
- PostgreSQL
- RabbitMQ
- MinIO
- OpenTelemetry Collector
- Prometheus
- Loki
- Tempo
- Grafana

The chart also includes:

- health probes
- ConfigMaps
- Secrets
- Grafana dashboards
- Prometheus alert rules
- Docker-in-Docker sidecar for worker deployment

### 12. Testing

The repository includes broad automated test coverage across modules.

- 52 test files are present across `cli`, `common`, `server`, and `worker`
- coverage includes parser, validator, planner, CLI, API, services, repositories, messaging, observability, DTOs, and artifacts
- `./gradlew test --no-daemon` passes successfully

## Implemented Extensions

The following capabilities go beyond the minimum base requirements and are implemented in the current codebase:

- `status` command and status APIs
- asynchronous server/worker execution architecture
- PostgreSQL-backed historical reporting
- RabbitMQ orchestration
- artifact collection and MinIO storage
- OpenTelemetry tracing
- Prometheus metrics
- structured JSON logging
- Grafana dashboards
- Prometheus alert rules
- Kubernetes Helm deployment

## Partly Implemented Features

The following features are present in part, but not yet complete end-to-end:

### Artifact Download

- artifacts are collected, stored, and reported
- direct download endpoints are not implemented

### Report Filtering And Pagination

- report drill-down is implemented
- filtering, pagination, and aggregate statistics are not implemented

### Log Aggregation Validation

- observability components are configured for Loki, Tempo, and Grafana
- end-to-end validation of the complete logging path is not documented here

## Current Limitations

The following capabilities are not implemented in the current codebase:

- webhook-triggered pipeline execution
- retrying failed jobs
- resuming failed pipelines
- pipeline templates, inheritance, or composition
- secrets injection into job containers
- artifact download API
- report pagination
- report filtering by date or status
- aggregate reporting metrics such as success-rate summaries
- multi-repository orchestration in a single pipeline
- build caching across runs

## Important Implementation Notes

- The implemented pipeline discovery path is `.pipelines/`
- The internal allow-failure extension is currently expressed with the YAML key `failures`
- `dryrun` shows execution order, but does not explicitly print dependency waves

## Submission Statement

The current project delivers a working remote-service CI/CD system with CLI access, validation, execution, reporting, messaging, persistence, artifact storage, observability support, and Kubernetes deployment assets.

The implemented system is functional, test-backed, and significantly extended beyond the basic project scope, while still leaving several advanced capabilities for future work.

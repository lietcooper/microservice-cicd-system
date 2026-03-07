# High-Level Design

## Overview

This document expands on the initial design with a detailed high-level architecture for our custom CI/CD system. The system allows developers to define, validate, and execute CI/CD pipelines locally (Phase 1) or remotely (Phase 2). All pipeline configuration is stored as YAML files in the repository under `.pipelines/`.

## High-Level Design Diagram
![Design Architecture](./images/my-diagram.drawio.svg)

### Component Communication Summary

| From | To | Protocol | Direction | Purpose |
|------|----|----------|-----------|---------|
| CLI | REST Service | HTTP/REST | Unidirectional (request) | Submit run requests (`repoUrl`, branch, commit, pipeline selector), query reports |
| REST Service | CLI | HTTP/REST | Unidirectional (response) | Return execution results, report data |
| REST Service | Git Repository | Git CLI | Unidirectional | Clone repo at specified branch/commit to obtain committed snapshot |
| REST Service | RabbitMQ | AMQP | Unidirectional | Publish pipeline execution messages with archived workspace snapshot |
| Worker | RabbitMQ | AMQP | Bidirectional | Consume pipeline/job messages and publish job results and status updates |
| Worker | Docker Engine | Docker API | Bidirectional | Pull images, create/start/stop containers, mount extracted workspace, capture output |
| REST Service | DataStore | SQL (JDBC) | Bidirectional | Read/write pipeline run, stage, and job status |

**Note:** The CLI also performs local-only operations (verify, dryrun) that do not involve the REST Service. These operations parse and validate YAML files entirely within the CLI process.

## System Components

### 1. CLI (Command Line Interface)

The CLI (`cicd`) is the primary user-facing interface built with picocli.

**Responsibilities:**
- Parse and validate pipeline configuration files locally for `verify` and `dryrun` (YAML v1.2)
- Compute and display execution order (dryrun)
- For `run`: send repository metadata (`repoUrl`, branch, commit, and pipeline selector) to the REST Service
- Optionally upload raw pipeline YAML when using file-based run mode
- Display pipeline status and reports to users

**Subcommands:**

| Command | Requires REST? | Description |
|---------|---------------|-------------|
| `verify` | No | Validate YAML configuration files locally |
| `dryrun` | No | Preview execution order without running |
| `run` | Yes | Execute a pipeline via the REST Service |
| `report` | Yes | Query execution history from the DataStore |

### 2. REST Service (Spring Boot)

The REST Service is the control plane. It accepts execution requests, creates committed Git snapshots, validates configuration, stores run metadata, and publishes execution work to RabbitMQ.

**Responsibilities:**
- Receive and process requests from CLI
- Clone the git repository at the specified branch and commit into a temporary directory
- Read and validate the pipeline configuration from `.pipelines/` in the cloned repo
- Archive the committed snapshot into a portable workspace bundle
- Publish execution requests to RabbitMQ
- Store and retrieve execution data from DataStore
- Track git metadata (branch, commit hash, repo) for each run
- Persist status updates received from workers
- Clean up the cloned temporary directory after the workspace archive is created

### 3. Worker Service (Spring Boot)

The Worker is the execution plane. It consumes pipeline messages, extracts the archived workspace, orchestrates stages and waves, runs jobs in Docker, and reports status back to the server.

**Responsibilities:**
- Consume pipeline execution messages from RabbitMQ
- Extract archived workspaces into temporary local directories
- Parse the pipeline YAML and compute stage/job wave ordering
- Dispatch job messages and collect job results
- Run jobs inside Docker containers with the extracted workspace mounted
- Publish pipeline, stage, and job status updates back to the server
- Clean up extracted workspaces after execution

**Key Endpoints:**

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/pipelines/execute` | Start a pipeline execution. Payload includes `repoUrl`, `branch`, `commit`, and `pipelineName` or `pipelineYaml` |
| `GET` | `/pipelines/{name}/runs` | Get all runs for a pipeline |
| `GET` | `/pipelines/{name}/runs/{runNo}` | Get a specific run |
| `GET` | `/pipelines/{name}/runs/{runNo}/stages/{stage}` | Get a specific stage in a run |
| `GET` | `/pipelines/{name}/runs/{runNo}/stages/{stage}/jobs/{job}` | Get a specific job |

### 4. DataStore (PostgreSQL)

The DataStore persists all pipeline execution data.

**Responsibilities:**
- Store pipeline execution history (run-no, status, timestamps, git info)
- Record stage execution details (pipeline, status, timestamps)
- Record job execution details (name, stage, pipeline, status, timestamps)
- Support querying historical runs for the `report` command

**Data Model:**

| Table | Key Fields |
|-------|------------|
| `pipeline_runs` | run-no, pipeline name, status, start-time, end-time, git-repo, git-branch, git-hash |
| `stage_runs` | pipeline, run-no, stage name, status, start-time, end-time |
| `job_runs` | pipeline, run-no, stage, job name, status, start-time, end-time |

**Storage Strategy:**
- Current implementation uses PostgreSQL for both local and remote-style deployments.

### 5. Docker Engine

Docker Engine runs the actual CI/CD jobs as containers.

**Responsibilities:**
- Pull Docker images specified in pipeline configuration (`image` field)
- Create and start containers for each job
- Execute `script` commands inside the container
- Return container output and exit codes to the Worker

## Deployment Modes

### Local Mode (Phase 1)

All components run on the same developer machine:

```
Developer Machine
├── CLI (cicd)              -- user runs commands here
├── REST Service            -- control plane
├── Worker Service          -- execution plane
├── DataStore (PostgreSQL)
├── RabbitMQ
└── Docker Engine           -- runs containers locally
```

### Remote Mode (Phase 2)

CLI runs locally, everything else runs remotely:

```
Developer Machine           Remote Server
├── CLI (cicd)              ├── REST Service
                            ├── Worker Service
                            ├── DataStore (PostgreSQL)
                            ├── RabbitMQ
                            └── Docker Engine
```

The transition from local to remote only requires changing the REST Service URL in the CLI configuration -- no architectural changes.

## Pros and Cons of This Design

### Pros

1. **Clean separation of concerns** -- Each component has a single responsibility (CLI for user interaction, REST for orchestration, DataStore for persistence, Docker for execution).
2. **Network-ready from day one** -- Using REST/HTTP between CLI and the service means the same architecture works for both local and remote modes without redesign.
3. **Technology flexibility** -- Components communicate via standard protocols (HTTP, SQL, Docker API), so individual components can be replaced independently (e.g., swap SQLite for PostgreSQL).
4. **Scalability path** -- The control plane and worker plane are separated, so execution can move to multiple workers later.
5. **Debuggability** -- REST APIs are easy to inspect and test independently using tools like curl or Postman.

### Cons

1. **Overhead for local mode** -- Running a REST service locally adds startup time and complexity compared to direct in-process calls.
2. **More moving parts** -- Developers need to have the REST Service running before they can use `run` or `report` commands.
3. **Network dependency** -- Even in local mode, the CLI-to-REST communication goes over HTTP, adding latency compared to direct function calls.
4. **Deployment complexity** -- Setting up the full system requires server, worker, RabbitMQ, PostgreSQL, and Docker.

### Why We Chose This Design

The primary requirement states that "the CLI component must communicate with other components in a way that allows the CLI to be on one machine and other components on different machines." This rules out a purely in-process architecture. By using REST/HTTP from the start, we avoid a costly redesign when transitioning from Phase 1 (local) to Phase 2 (remote). The overhead of running a local REST service is an acceptable trade-off for architectural consistency and a clean Phase 2 migration path.

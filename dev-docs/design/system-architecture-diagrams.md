# System Architecture Diagrams

## Diagram 1: High-Level System Architecture

This diagram walks through the end-to-end execution flow of `cicd run --name default`, showing
the order of execution, what each component does, how data flows, and where data gets persisted.

- Simplified architecture
![high-level-architecture-demo.svg](images/high-level-architecture-demo.svg)
- Detailed architecture:
```mermaid
flowchart TB
    subgraph dev["Developer Machine"]
        CLI["CLI<br/><i>cicd run --name default</i><br/>(PicoCLI)"]
    end

    subgraph infra["Server Infrastructure"]
        subgraph server["Server (Spring Boot :8080)"]
            REST["REST API<br/>PipelineExecutionController"]
            GIT["GitRepositoryService<br/><i>clone repo &rarr; create tar.gz archive</i>"]
            PUB["PipelineMessagePublisher"]
            SLISTENER["StatusUpdateListener<br/><i>persists status to DB</i>"]
        end

        subgraph mq["RabbitMQ (AMQP)"]
            Q_PIPE["cicd.pipeline.execute"]
            Q_JOB["cicd.job.execute"]
            Q_RESULT["cicd.job.results"]
            Q_STATUS["cicd.status.update"]
            Q_EVENTS["cicd.events"]
            Q_DLX["cicd.dead-letters"]
        end

        subgraph worker["Worker (Spring Boot :8081)"]
            ORCH["PipelineOrchestrationListener<br/><i>parse YAML, compute waves,<br/>dispatch jobs per stage</i>"]
            JOBEXEC["JobExecutionListener<br/><i>run Docker container,<br/>collect artifacts</i>"]
            JOBRES["JobResultListener<br/><i>fan-in: track wave completion</i>"]
            STATPUB["StatusUpdatePublisher<br/>StatusEventPublisher"]
        end

        PG[("PostgreSQL<br/><b>pipeline_runs</b><br/><b>stage_runs</b><br/><b>job_runs</b><br/><b>artifacts</b>")]
        MINIO[("MinIO (S3)<br/><b>Artifact Storage</b><br/><i>{pipeline}/{run}/{stage}/{job}/...</i>")]
        DOCKER["Docker Engine<br/><i>pull image, create container,<br/>mount workspace, run scripts</i>"]
        GITREPO[("Git Repository<br/>(local or remote)")]

        subgraph obs["Observability Stack"]
            OTEL["OpenTelemetry<br/>Collector"]
            PROM["Prometheus"]
            TEMPO["Tempo"]
            LOKI["Loki"]
            GRAF["Grafana"]
        end
    end

    %% ── Execution Flow (numbered) ──

    CLI -- "① HTTP POST /api/pipelines/execute<br/>{repoUrl, branch, commit, pipelineName}" --> REST
    REST -- "② git clone --branch --commit" --> GITREPO
    GIT -- "③ create workspace tar.gz" --> REST
    REST -- "④ INSERT pipeline_run (status=PENDING)" --> PG
    REST -- "⑤ publish PipelineExecuteMessage<br/>(includes workspace archive bytes)" --> PUB
    PUB --> Q_PIPE

    Q_PIPE -- "⑥ consume" --> ORCH
    ORCH -- "⑦ dispatch JobExecuteMessage<br/>(per job in wave)" --> Q_JOB
    Q_JOB -- "⑧ consume" --> JOBEXEC
    JOBEXEC -- "⑨ pull image, run scripts" --> DOCKER
    JOBEXEC -- "⑩ upload artifacts" --> MINIO
    JOBEXEC -- "⑪ publish JobResultMessage" --> Q_RESULT

    Q_RESULT -- "⑫ consume" --> JOBRES
    JOBRES -. "wave complete<br/>→ next wave or stage" .-> ORCH
    STATPUB -- "⑬ publish StatusUpdateMessage" --> Q_STATUS
    STATPUB -- "publish StatusEventMessage" --> Q_EVENTS

    Q_STATUS -- "⑭ consume" --> SLISTENER
    SLISTENER -- "⑮ UPDATE pipeline_runs,<br/>stage_runs, job_runs" --> PG

    CLI -- "⑯ HTTP GET /api/reports/{name}/{runNo}<br/>or /pipelines/{name}/status" --> REST
    REST -- "query" --> PG
    REST -- "response with run report" --> CLI

    %% ── Observability ──
    server -. "traces + metrics" .-> OTEL
    worker -. "traces + metrics" .-> OTEL
    OTEL -. "metrics" .-> PROM
    OTEL -. "traces" .-> TEMPO
    OTEL -. "logs" .-> LOKI
    PROM --> GRAF
    TEMPO --> GRAF
    LOKI --> GRAF

    %% ── Styling ──
    style PG fill:#336791,color:#fff
    style MINIO fill:#c72c48,color:#fff
    style DOCKER fill:#2496ed,color:#fff
    style GITREPO fill:#f05033,color:#fff
    style Q_PIPE fill:#ff6600,color:#fff
    style Q_JOB fill:#ff6600,color:#fff
    style Q_RESULT fill:#ff6600,color:#fff
    style Q_STATUS fill:#ff6600,color:#fff
    style Q_EVENTS fill:#ff6600,color:#fff
    style Q_DLX fill:#993300,color:#fff
    style CLI fill:#4a90d9,color:#fff
    style REST fill:#6db33f,color:#fff
    style ORCH fill:#6db33f,color:#fff
    style JOBEXEC fill:#6db33f,color:#fff
```

### Execution Order Summary

| Step | Component | Action | Data Persisted |
|------|-----------|--------|----------------|
| ① | CLI → Server | HTTP POST with repoUrl, branch, commit, pipelineName | -- |
| ② | Server | Clone git repo at specified branch/commit | -- |
| ③ | Server | Create tar.gz workspace archive from cloned repo | -- |
| ④ | Server → PostgreSQL | Insert `pipeline_runs` row (status=PENDING) | `pipeline_runs` |
| ⑤ | Server → RabbitMQ | Publish `PipelineExecuteMessage` (includes archive bytes + traceId) | -- |
| ⑥ | Worker | Consume pipeline message, extract archive, parse YAML, compute wave order | -- |
| ⑦ | Worker → RabbitMQ | Dispatch `JobExecuteMessage` for each job in current wave | -- |
| ⑧ | Worker | Consume job message | -- |
| ⑨ | Worker → Docker | Pull image, create container with workspace mounted, execute scripts | -- |
| ⑩ | Worker → MinIO | Upload collected artifacts to S3 storage | MinIO objects |
| ⑪ | Worker → RabbitMQ | Publish `JobResultMessage` (exitCode, output, artifactPaths) | -- |
| ⑫ | Worker | Fan-in: track wave completion via CountDownLatch | -- |
| ⑬ | Worker → RabbitMQ | Publish `StatusUpdateMessage` for pipeline/stage/job state changes | -- |
| ⑭ | Server | Consume status updates from queue | -- |
| ⑮ | Server → PostgreSQL | Update `pipeline_runs`, `stage_runs`, `job_runs`, `artifacts` | All 4 tables |
| ⑯ | CLI → Server | HTTP GET for reports/status → query PostgreSQL → return to CLI | -- |

### Where Data Gets Persisted

| Store | What | Durability |
|-------|------|------------|
| **PostgreSQL** | Pipeline runs, stage runs, job runs, artifact metadata (timestamps, status, git info) | Permanent (survives restarts in remote mode) |
| **MinIO (S3)** | Artifact files (`{pipeline}/{run-no}/{stage}/{job}/{file}`) | Permanent |
| **RabbitMQ** | In-flight messages (durable queues with DLX for failed messages) | Transient (delivery guarantee) |

---

## Diagram 2: System Components Detail

### 2a. RabbitMQ Message Topology

Each row below is one message channel: **Publisher → Exchange → Queue → Consumer**.
Queues marked with DLX route failed messages to the dead-letter queue for inspection.

#### Channel 1: Pipeline Dispatch (Server → Worker)

```mermaid
flowchart LR
    P1["Server<br/>PipelineMessagePublisher"]
    E1["cicd.pipeline.direct<br/><i>DirectExchange</i>"]
    Q1["cicd.pipeline.execute<br/><i>DLX enabled</i>"]
    C1["Worker<br/>PipelineOrchestrationListener"]

    P1 -- "routing key:<br/>pipeline.execute" --> E1 --> Q1 --> C1
    Q1 -. "on failure" .-> DLX1["cicd.dead-letters"]

    style E1 fill:#ff6600,color:#fff
    style DLX1 fill:#993300,color:#fff
```

> Server publishes a `PipelineExecuteMessage` (pipeline YAML + workspace tar.gz + git metadata).
> Worker's orchestrator consumes it and begins stage/wave execution.

#### Channel 2: Job Dispatch (Worker Orchestrator → Worker Job Executor)

```mermaid
flowchart LR
    P2["Worker<br/>PipelineOrchestrationListener"]
    E2["cicd.job.direct<br/><i>DirectExchange</i>"]
    Q2["cicd.job.execute<br/><i>DLX enabled</i>"]
    C2["Worker<br/>JobExecutionListener"]

    P2 -- "routing key:<br/>job.execute" --> E2 --> Q2 --> C2
    Q2 -. "on failure" .-> DLX2["cicd.dead-letters"]

    style E2 fill:#ff6600,color:#fff
    style DLX2 fill:#993300,color:#fff
```

> Orchestrator dispatches one `JobExecuteMessage` per job in the current wave.
> Job executor runs the Docker container and collects artifacts.

#### Channel 3: Job Results (Worker Job Executor → Worker Orchestrator)

```mermaid
flowchart LR
    P3["Worker<br/>JobExecutionListener"]
    E3["cicd.job-results.direct<br/><i>DirectExchange</i>"]
    Q3["cicd.job.results"]
    C3["Worker<br/>JobResultListener"]

    P3 -- "routing key:<br/>job.result" --> E3 --> Q3 --> C3

    style E3 fill:#ff6600,color:#fff
```

> Job executor publishes a `JobResultMessage` (exit code, output, artifact paths).
> Result listener records it in the wave tracker and counts down the fan-in latch.

#### Channel 4: Status Updates (Worker → Server)

```mermaid
flowchart LR
    P4["Worker<br/>StatusUpdatePublisher"]
    E4["cicd.status.direct<br/><i>DirectExchange</i>"]
    Q4["cicd.status.update<br/><i>DLX enabled</i>"]
    C4["Server<br/>StatusUpdateListener"]

    P4 -- "routing key:<br/>status.update" --> E4 --> Q4 --> C4
    Q4 -. "on failure" .-> DLX4["cicd.dead-letters"]

    style E4 fill:#ff6600,color:#fff
    style DLX4 fill:#993300,color:#fff
```

> Worker publishes `StatusUpdateMessage` on every state change (pipeline/stage/job RUNNING, SUCCESS, FAILED).
> Server consumes and persists to PostgreSQL. This is how execution results reach the database.

#### Channel 5: Events Stream (Worker → Monitoring)

```mermaid
flowchart LR
    P5["Worker<br/>StatusEventPublisher"]
    E5["cicd.events.topic<br/><i>TopicExchange</i>"]
    Q5["cicd.events"]
    C5["(monitoring /<br/>future consumers)"]

    P5 -- "routing key: #" --> E5 --> Q5 -.-> C5

    style E5 fill:#ff6600,color:#fff
    style C5 stroke-dasharray: 5 5
```

> Worker publishes `StatusEventMessage` for real-time notifications (started, completed).
> Currently no active consumer -- available for future dashboards or webhooks.

#### Dead Letter Exchange (failed messages sink)

```mermaid
flowchart LR
    SRC1["cicd.pipeline.execute"] -. "rejected /<br/>expired" .-> DLX
    SRC2["cicd.job.execute"] -. "rejected /<br/>expired" .-> DLX
    SRC3["cicd.status.update"] -. "rejected /<br/>expired" .-> DLX

    DLX["cicd.dlx<br/><i>DirectExchange</i>"]
    DLQ["cicd.dead-letters<br/><i>diagnostic sink</i>"]

    DLX -- "#" --> DLQ

    style DLX fill:#993300,color:#fff
    style DLQ fill:#993300,color:#fff
```

> Three critical queues (pipeline, job, status) route failures here.
> Two non-critical queues (job results, events) do not -- timeouts handle those cases.
> No active consumer: messages accumulate for manual inspection via RabbitMQ Management UI.

#### Message Schemas

**PipelineExecuteMessage** (Server → Worker via `cicd.pipeline.execute`)

| Field | Type | Description |
|-------|------|-------------|
| `pipelineRunId` | Long | DB primary key for the pipeline run |
| `pipelineName` | String | Pipeline name from YAML |
| `runNo` | int | Auto-incremented run number |
| `pipelineYaml` | String | Raw YAML content of the pipeline config |
| `workspaceArchive` | byte[] | tar.gz of the git repo snapshot |
| `gitBranch` | String | Branch checked out |
| `gitCommit` | String | Commit hash |
| `traceId` | String | OpenTelemetry trace ID for distributed tracing |

**JobExecuteMessage** (Worker Orchestrator → Worker Job Executor via `cicd.job.execute`)

| Field | Type | Description |
|-------|------|-------------|
| `pipelineRunId` | Long | Pipeline run FK |
| `correlationId` | String | Unique ID for wave fan-in coordination |
| `jobName` | String | Job name from YAML |
| `stageName` | String | Stage this job belongs to |
| `pipelineName` | String | Pipeline name |
| `runNo` | int | Run number |
| `image` | String | Docker image to pull |
| `scripts` | List\<String\> | Commands to execute in container |
| `workspacePath` | String | Path to extracted workspace on disk |
| `totalJobsInWave` | int | Total jobs in this wave (for fan-in) |
| `allowFailure` | boolean | Whether job failure is non-blocking |
| `artifacts` | List\<String\> | Glob patterns for artifact collection |

**JobResultMessage** (Worker Job Executor → Worker Orchestrator via `cicd.job.results`)

| Field | Type | Description |
|-------|------|-------------|
| `pipelineRunId` | Long | Pipeline run FK |
| `correlationId` | String | Matches the wave correlation ID |
| `jobName` | String | Job that completed |
| `stageName` | String | Stage of the job |
| `pipelineName` | String | Pipeline name |
| `success` | boolean | Whether the job succeeded |
| `exitCode` | int | Docker container exit code |
| `output` | String | Captured container stdout/stderr |
| `allowFailure` | boolean | Whether failure is tolerated |
| `collectedArtifacts` | List\<String\> | MinIO storage paths of uploaded artifacts |

**StatusUpdateMessage** (Worker → Server via `cicd.status.update`)

| Field | Type | Description |
|-------|------|-------------|
| `entityType` | String | `"PIPELINE"`, `"STAGE"`, or `"JOB"` |
| `pipelineRunId` | Long | Pipeline run FK |
| `pipelineName` | String | Pipeline name |
| `runNo` | int | Run number |
| `stageName` | String | Null for pipeline-level updates |
| `stageOrder` | Integer | Stage position (for creating stage records) |
| `jobName` | String | Null for pipeline/stage-level updates |
| `status` | String | `"PENDING"`, `"RUNNING"`, `"SUCCESS"`, `"FAILED"` |
| `startTime` | OffsetDateTime | When execution started |
| `endTime` | OffsetDateTime | When execution ended |
| `allowFailure` | Boolean | Job allow-failure flag |
| `traceId` | String | Trace ID (pipeline-level only) |
| `artifactPatterns` | List\<String\> | YAML artifact patterns (job-level) |
| `artifactStoragePaths` | List\<String\> | MinIO paths (job completion) |

**StatusEventMessage** (Worker → Events Topic via `cicd.events`)

| Field | Type | Description |
|-------|------|-------------|
| `eventType` | String | Event category (e.g., `"STARTED"`, `"COMPLETED"`) |
| `pipelineRunId` | Long | Pipeline run FK |
| `pipelineName` | String | Pipeline name |
| `runNo` | int | Run number |
| `stageName` | String | Stage name (if applicable) |
| `jobName` | String | Job name (if applicable) |
| `status` | String | Current status |
| `timestamp` | OffsetDateTime | When the event occurred |
| `message` | String | Human-readable event description |

---

### 2b. Database ER Diagram

```mermaid
erDiagram
    pipeline_runs {
        BIGSERIAL id PK
        VARCHAR pipeline_name "NOT NULL, part of UQ"
        INT run_no "NOT NULL, part of UQ"
        VARCHAR status "NOT NULL (PENDING|RUNNING|SUCCESS|FAILED)"
        TIMESTAMPTZ start_time
        TIMESTAMPTZ end_time
        VARCHAR git_hash
        VARCHAR git_branch
        VARCHAR git_repo
        VARCHAR trace_id "OpenTelemetry trace ID"
    }

    stage_runs {
        BIGSERIAL id PK
        BIGINT pipeline_run_id FK "NOT NULL → pipeline_runs.id (CASCADE)"
        VARCHAR stage_name "NOT NULL"
        INT stage_order "NOT NULL"
        VARCHAR status "NOT NULL (PENDING|RUNNING|SUCCESS|FAILED)"
        TIMESTAMPTZ start_time
        TIMESTAMPTZ end_time
    }

    job_runs {
        BIGSERIAL id PK
        BIGINT stage_run_id FK "NOT NULL → stage_runs.id (CASCADE)"
        VARCHAR job_name "NOT NULL"
        VARCHAR status "NOT NULL (PENDING|RUNNING|SUCCESS|FAILED)"
        TIMESTAMPTZ start_time
        TIMESTAMPTZ end_time
        BOOLEAN allow_failure "DEFAULT FALSE"
    }

    artifacts {
        BIGSERIAL id PK
        BIGINT job_run_id FK "NOT NULL → job_runs.id"
        VARCHAR pattern "NOT NULL (glob pattern from YAML)"
        VARCHAR storage_path "NOT NULL (MinIO object key)"
    }

    pipeline_runs ||--o{ stage_runs : "has stages"
    stage_runs ||--o{ job_runs : "has jobs"
    job_runs ||--o{ artifacts : "produces artifacts"
```

#### Indexes

| Index | Table | Column(s) | Purpose |
|-------|-------|-----------|---------|
| `uq_pipeline_run` | `pipeline_runs` | `(pipeline_name, run_no)` | Unique constraint |
| `idx_pipeline_runs_name` | `pipeline_runs` | `pipeline_name` | Fast lookup by name |
| `idx_stage_runs_pipeline` | `stage_runs` | `pipeline_run_id` | Join performance |
| `idx_job_runs_stage` | `job_runs` | `stage_run_id` | Join performance |

#### Cascade Behavior

- Deleting a `pipeline_runs` row cascades to its `stage_runs`
- Deleting a `stage_runs` row cascades to its `job_runs`
- `artifacts` reference `job_runs` (no cascade defined — explicit cleanup needed)

---

### 2c. Worker Internals: Wave-Based Execution

```mermaid
flowchart TD
    MSG["PipelineExecuteMessage<br/>from cicd.pipeline.execute"]
    ORCH["PipelineOrchestrationListener"]
    EXTRACT["Extract workspace archive<br/>to /tmp/cicd-workspaces/{id}"]
    PARSE["Parse pipeline YAML<br/>(YamlParser + Validator)"]
    PLAN["ExecutionPlanner.computeWaveOrder()<br/><i>topological sort → group into waves</i>"]

    STAGE_LOOP{"For each stage<br/>(in defined order)"}
    WAVE_LOOP{"For each wave<br/>(in dependency order)"}

    REGISTER["StageCoordinatorService<br/>registerWave(correlationId, jobCount)<br/><i>creates CountDownLatch</i>"]
    DISPATCH["Dispatch JobExecuteMessage<br/>for each job in wave"]

    JOBEXEC["JobExecutionListener"]
    DOCKER["DockerRunner.runJob()<br/>1. Pull image (300s timeout)<br/>2. Create container (workspace at /workspace)<br/>3. Run scripts (joined with &&)<br/>4. Capture logs + exit code (600s timeout)"]
    ARTIFACTS["ArtifactCollectorService<br/><i>glob patterns → matched files</i>"]
    UPLOAD["ArtifactStorageService<br/><i>upload to MinIO</i>"]
    RESULT["Publish JobResultMessage<br/>to cicd.job.results"]

    FANIN["JobResultListener<br/>records result → countDown latch"]
    WAIT["StageCoordinatorService<br/>waitForWave(correlationId)<br/><i>blocks up to 30 min</i>"]
    CHECK{"All jobs<br/>succeeded?"}

    NEXT_WAVE["Proceed to next wave"]
    NEXT_STAGE["Proceed to next stage"]
    DONE["Publish PIPELINE SUCCESS<br/>StatusUpdateMessage"]
    FAIL["Publish PIPELINE FAILED<br/>StatusUpdateMessage"]
    CLEANUP["Clean up workspace<br/>/tmp/cicd-workspaces/{id}"]

    MSG --> ORCH
    ORCH --> EXTRACT --> PARSE --> PLAN
    PLAN --> STAGE_LOOP
    STAGE_LOOP --> WAVE_LOOP
    WAVE_LOOP --> REGISTER --> DISPATCH

    DISPATCH --> JOBEXEC
    JOBEXEC --> DOCKER --> ARTIFACTS --> UPLOAD --> RESULT
    RESULT --> FANIN
    FANIN --> WAIT

    WAIT --> CHECK
    CHECK -- "Yes" --> NEXT_WAVE
    CHECK -- "No (and not allow_failure)" --> FAIL
    NEXT_WAVE --> WAVE_LOOP
    WAVE_LOOP -- "all waves done" --> NEXT_STAGE
    NEXT_STAGE --> STAGE_LOOP
    STAGE_LOOP -- "all stages done" --> DONE
    DONE --> CLEANUP
    FAIL --> CLEANUP

    style DOCKER fill:#2496ed,color:#fff
    style UPLOAD fill:#c72c48,color:#fff
    style FANIN fill:#ff6600,color:#fff
    style REGISTER fill:#ff6600,color:#fff
```

#### Wave Execution Example

Given this pipeline with `needs` dependencies:

```yaml
stages:
  - build
  - test

compile:
  stage: build
  image: gradle:jdk21
  script: gradle build

lint:
  stage: test
  image: gradle:jdk21
  script: gradle checkstyle

unit-tests:
  stage: test
  image: gradle:jdk21
  needs: [lint]
  script: gradle test

integration-tests:
  stage: test
  image: gradle:jdk21
  needs: [unit-tests]
  script: gradle integrationTest
```

The ExecutionPlanner produces:

| Stage | Wave | Jobs (run in parallel) |
|-------|------|----------------------|
| build | 1 | `compile` |
| test | 1 | `lint` |
| test | 2 | `unit-tests` (needs lint) |
| test | 3 | `integration-tests` (needs unit-tests) |

- Wave 1 of `test` starts after `build` stage completes
- Wave 2 starts only after all Wave 1 jobs finish
- Wave 3 starts only after all Wave 2 jobs finish
- Jobs within the same wave run concurrently

# Sequence Diagrams

## 1. Pipeline Execution (`cicd run`)

Happy path — all stages and jobs succeed.

```mermaid
sequenceDiagram
    participant User
    participant CLI
    participant REST as REST Service (Spring Boot)
    participant Git as Git Repository
    participant MQ as RabbitMQ
    participant Worker as Worker Service
    participant DB as DataStore (PostgreSQL)
    participant Docker as Docker Engine

    User->>CLI: cicd run --repo-url <url> --name default
    CLI->>REST: POST /api/pipelines/execute {repoUrl, branch, commit, pipelineName}

    REST->>Git: git clone repoUrl --branch branch
    Git-->>REST: cloned repo in temp directory
    REST->>REST: git checkout commit
    REST->>REST: read .pipelines/, find pipeline by name, validate config
    REST->>REST: archive snapshot into workspace bundle

    REST->>DB: INSERT pipeline_runs (status=PENDING, start_time, git info)
    DB-->>REST: run_no
    REST->>MQ: publish PipelineExecuteMessage(workspaceArchive, pipelineYaml)
    REST->>REST: delete temp git snapshot
    REST-->>CLI: 202 Accepted (pipelineName, runNo)

    MQ->>Worker: consume PipelineExecuteMessage
    Worker->>Worker: extract workspace archive
    Worker->>DB: status update via REST-side listener queue

    loop each Stage (sequential)
        Worker->>MQ: publish stage/job status updates
        loop each Wave (parallelizable jobs)
            Worker->>MQ: publish JobExecuteMessage(workspacePath)
            Worker->>Docker: pull image and start container
            Docker-->>Worker: container output + exit code
            Worker->>MQ: publish JobResultMessage + status updates
        end
    end

    Worker->>Worker: cleanup extracted workspace
    CLI->>REST: GET /pipelines/default/runs/1
    REST-->>CLI: run status / report data
    CLI-->>User: display results
```

### Step Descriptions

- **CLI request**: CLI sends `repoUrl`, optional `branch` / `commit`, and pipeline selection to the REST Service.
- **Git snapshot**: REST Service clones the repository, checks out the requested branch/commit, and validates the pipeline from committed code only.
- **Workspace archive**: REST Service archives the committed snapshot and publishes it to RabbitMQ with the pipeline metadata.
- **Async execution**: Worker extracts the archive, computes stage and wave ordering, and dispatches jobs.
- **Docker execution**: Each job runs in Docker with the extracted workspace mounted locally on the worker host.
- **Status persistence**: Worker publishes status updates; server listeners persist pipeline, stage, and job state into PostgreSQL.
- **Cleanup**: Server deletes the clone after archiving; worker deletes the extracted workspace after execution.
- **Report flow**: CLI polls the report API to inspect queued, running, success, or failed runs.

---

## 2. Report Request (`cicd report`)

Query the report for a specific pipeline run.

```mermaid
sequenceDiagram
    participant User
    participant CLI
    participant REST as REST Service (Spring Boot)
    participant DB as DataStore (SQLite / PostgreSQL)

    User->>CLI: cicd report --pipeline default --run 1
    CLI->>CLI: parse arguments

    CLI->>REST: GET /pipelines/default/runs/1
    REST->>DB: SELECT FROM pipeline_runs WHERE name='default' AND run_no=1
    DB-->>REST: run record (status, start_time, end_time, git info)

    REST->>DB: SELECT FROM stage_runs WHERE pipeline='default' AND run_no=1
    DB-->>REST: stage records (name, status, timestamps)

    REST->>DB: SELECT FROM job_runs WHERE pipeline='default' AND run_no=1
    DB-->>REST: job records (name, stage, status, timestamps)

    REST->>REST: assemble report (aggregate stats, calculate durations)
    REST-->>CLI: JSON response (full report data)

    CLI->>CLI: format output (table / plain / json)
    CLI-->>User: display report
```

### Step Descriptions

- **Parse arguments**: CLI extracts pipeline name (`default`) and run number (`1`) from the command
- **GET /pipelines/default/runs/1**: CLI sends the report request to the REST Service over HTTP
- **Query pipeline run**: REST Service queries the `pipeline_runs` table for the matching run record
- **Query stages**: REST Service queries `stage_runs` for all stages in that run
- **Query jobs**: REST Service queries `job_runs` for all jobs in that run
- **Assemble report**: REST Service aggregates the data — total duration, per-stage/per-job timing, success/failure counts
- **Return report**: REST Service sends the assembled report back as JSON
- **Format & display**: CLI formats the output based on user preference (table, plain text, or raw JSON) and displays it

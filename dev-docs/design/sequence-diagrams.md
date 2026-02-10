# Sequence Diagrams

## 1. Pipeline Execution (`cicd run`)

Happy path — all stages and jobs succeed.

```mermaid
sequenceDiagram
    participant User
    participant CLI
    participant REST as REST Service (Spring Boot)
    participant DB as DataStore (PostgreSQL)
    participant Docker as Docker Engine

    User->>CLI: cicd run --config pipeline.yaml
    CLI->>CLI: parse YAML, validate config
    CLI->>CLI: collect git metadata (repo, branch, commit hash)
    CLI->>REST: POST /pipelines/run (config + git info)

    REST->>DB: INSERT pipeline_runs (status=RUNNING, start_time, git info)
    DB-->>REST: run_no

    loop each Stage (sequential, in dependency order)
        REST->>DB: INSERT stage_runs (status=RUNNING, start_time)
        DB-->>REST: ok

        loop each Job (respecting needs order within stage)
            REST->>Docker: pull image (Docker API)
            Docker-->>REST: image ready
            REST->>Docker: create & start container (script commands)
            Docker-->>REST: container output + exit code 0
            REST->>DB: INSERT job_runs (status=SUCCESS, start_time, end_time)
            DB-->>REST: ok
        end

        REST->>DB: UPDATE stage_runs (status=SUCCESS, end_time)
        DB-->>REST: ok
    end

    REST->>DB: UPDATE pipeline_runs (status=SUCCESS, end_time)
    DB-->>REST: ok
    REST-->>CLI: JSON response (run summary, status)
    CLI-->>User: display results
```

### Step Descriptions

- **Parse & validate**: CLI reads the YAML file and validates it locally (same logic as `verify`)
- **Git metadata**: CLI collects current repo, branch, and commit hash to attach to the run record
- **POST /pipelines/run**: CLI sends the pipeline config and git info to the REST Service over HTTP
- **Create pipeline run**: REST Service inserts a new row in `pipeline_runs` table with status RUNNING, DB returns the assigned run_no
- **Stage loop**: Stages execute sequentially based on dependency order. For each stage, a row is inserted into `stage_runs`
- **Job loop**: Within a stage, jobs run respecting `needs` ordering. REST Service pulls the Docker image, starts a container, and runs the script commands
- **Record job result**: After each container finishes, REST Service writes the result to `job_runs` (status, timestamps)
- **Update stage/pipeline**: Once all jobs in a stage succeed, the stage is marked SUCCESS. After all stages complete, the pipeline run is marked SUCCESS
- **Return results**: REST Service returns a JSON response with the run summary; CLI formats and displays it

---

## 2. Report Request (`cicd report`)

Query the report for a specific pipeline run.

```mermaid
sequenceDiagram
    participant User
    participant CLI
    participant REST as REST Service (Spring Boot)
    participant DB as DataStore (PostgreSQL)

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
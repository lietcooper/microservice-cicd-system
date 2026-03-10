# Beta Release Report

## Section 1: Features Implemented

### CLI Subcommands
- `cicd verify <file>` — validates a single YAML pipeline configuration file
- `cicd verify <directory>` — validates all YAML files in a directory, including cross-file pipeline name uniqueness check
- `cicd dryrun <file>` — validates and prints the execution order of stages and jobs
- `cicd run --name <name>` — executes a pipeline by name, looked up from `.pipelines/` directory
- `cicd run --file <file>` — executes a pipeline from a local YAML file
- `cicd run --branch <branch>` — optionally targets a specific Git branch
- `cicd run --commit <commit>` — optionally targets a specific Git commit
- `cicd run --server <url>` — configures the server URL (default: `http://localhost:8080`)
- `cicd report --pipeline <name>` — retrieves all runs for a pipeline
- `cicd report --pipeline <name> --run <n>` — retrieves a specific run with stage summaries
- `cicd report --pipeline <name> --run <n> --stage <stage>` — retrieves a specific stage with job details
- `cicd report --pipeline <name> --run <n> --stage <stage> --job <job>` — retrieves a specific job

### YAML Validation
- Required fields: `pipeline.name`, job `stage`, `image`, `script`
- At least one stage defined
- No duplicate stage names
- No empty stages (every defined stage must have at least one job)
- No duplicate job names within a file
- `needs` list must be non-empty when specified
- No duplicate entries in a `needs` list
- Jobs in `needs` must exist and belong to the same stage
- Cycle detection in `needs` dependencies with descriptive error messages
- Error messages include filename, line number, and column number

### Pipeline Execution
- Server-side Git repository cloning via `git clone`
- Branch and commit checkout and validation
- Pipeline YAML parsing and validation on server side
- Sequential stage execution in dependency order
- Job execution inside Docker containers using `LocalDockerRunner`
- Pipeline stops on first job failure within a stage
- Stage stops on first job failure
- Execution status tracking: `PENDING`, `RUNNING`, `SUCCESS`, `FAILED`

### Data Persistence
- PostgreSQL database with Flyway migrations
- `pipeline_runs` table: stores run number, status, timestamps, git metadata (repo, branch, hash)
- `stage_runs` table: stores stage name, order, status, and timestamps per run
- `job_runs` table: stores job name, status, and timestamps per stage run
- Auto-incrementing run number per pipeline name

### REST API
- `POST /api/pipelines/execute` — queues a pipeline for execution, returns `202 Accepted` with run number
- `GET /pipelines/{name}/runs` — all runs for a pipeline
- `GET /pipelines/{name}/runs/{runNo}` — specific run with stage summaries
- `GET /pipelines/{name}/runs/{runNo}/stages/{stageName}` — specific stage with job details
- `GET /pipelines/{name}/runs/{runNo}/stages/{stageName}/jobs/{jobName}` — specific job

### Message Queue Architecture
- RabbitMQ integration: server publishes pipeline execution messages to a queue
- Worker service consumes pipeline messages, orchestrates stage and job execution
- Jobs dispatched as individual messages to a job execution queue
- Wave-based parallel job dispatch within a stage (respecting `needs` ordering)
- Status updates published back to server via separate status update queue
- Server-side status listener updates database from worker status messages

### Infrastructure
- Docker Compose configuration for local PostgreSQL and RabbitMQ
- Docker Compose evaluator configuration for end-to-end testing
- Workspace archiving: server creates a tar archive of the cloned repo and sends it to the worker
- Workspace cleanup after job completion

### Code Quality
- JaCoCo test coverage reporting
- Checkstyle enforcement
- SpotBugs static analysis
- Javadoc generation for all public APIs
- Multi-module Gradle build (`common`, `cli`, `server`, `worker`)

---

## Section 2: Implementation Limitations

### Branch and Commit Switching
The `--branch` and `--commit` options on `cicd run` do not enforce the requirement that the CLI must exit with an error when the requested branch or commit differs from what is currently checked out locally. The server clones the repository fresh regardless, so mismatched local state is not detected.

### Asynchronous Execution and Run Status
Pipeline execution is asynchronous. After `cicd run` returns, the pipeline is in `PENDING` status and execution continues in the background via the worker. The CLI does not poll or wait for completion. Users must manually query `cicd report` to check the final status.

### No Git Metadata in Report for git-repo Field
The `git-repo` field in report output stores the local file path of the cloned snapshot directory rather than the original remote repository URL provided by the user.

### Job Output Not Stored
Container stdout and stderr are printed to the worker's console log but are not persisted to the database. The `report` command does not return job output or logs.

### No Artifact Upload
Artifacts produced by pipeline jobs (test reports, build outputs, etc.) are not collected, stored, or made available after execution.

### Parallel Job Execution
Jobs within a stage that have no `needs` dependency on each other are dispatched as a wave and run in parallel via the worker. However, ordering within a wave is non-deterministic and there is no configurable concurrency limit.

### Single Worker Instance
The worker is designed to run as a single instance. Running multiple worker instances simultaneously is untested and may cause race conditions in stage coordination (the `StageCoordinatorService` wave tracker is in-memory and not shared).

### No Remote Mode CLI Support
The CLI communicates with the server over HTTP, but there is no mechanism to configure TLS, authentication, or other security controls for remote deployments. The `--server` flag accepts any URL but the system is designed and tested for local use only.

### PipelineExecutorService Not Active
The legacy synchronous `PipelineExecutorService` class exists in the server module but is not registered as a Spring bean and is not used. All execution goes through the RabbitMQ worker path.

### Error Recovery
If the worker process crashes mid-execution, the pipeline run record remains in `RUNNING` status indefinitely. There is no timeout, retry, or dead-letter queue handling to recover stuck runs.

### Report git-repo Display
The `cicd report` output shows `git-repo` as the server-side temporary snapshot path, not the original repository URL. This does not match the format shown in the requirements specification.

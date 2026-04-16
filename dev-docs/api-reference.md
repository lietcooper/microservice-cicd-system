# Server API Reference

The CI/CD server exposes a REST API on port `8080`. All responses are JSON.

## Pipeline Execution

### POST /api/pipelines/execute

Submit a pipeline for asynchronous execution.

**Request body** (JSON):

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `repoUrl` | String | Yes | Git repository URL to clone |
| `pipelineName` | String | One of `pipelineName` or `pipelineYaml` | Name of a pipeline defined in the repo's `.pipelines/` directory |
| `pipelineYaml` | String | One of `pipelineName` or `pipelineYaml` | Raw YAML pipeline content |
| `branch` | String | No | Git branch to checkout (defaults to repo's default branch) |
| `commit` | String | No | Git commit hash (defaults to HEAD of branch) |

**Response** (`202 Accepted`):

```json
{
  "runId": 1,
  "pipelineName": "default",
  "runNo": 1,
  "status": "PENDING",
  "startTime": "2025-08-29T16:17:52-07:00",
  "endTime": null,
  "gitBranch": "main",
  "gitHash": "c3aefda",
  "message": "Pipeline execution queued"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `runId` | Long | Internal run identifier |
| `pipelineName` | String | Pipeline name |
| `runNo` | Integer | Sequential run number for this pipeline |
| `status` | String | `PENDING`, `RUNNING`, `SUCCESS`, or `FAILED` |
| `startTime` | ISO 8601 | When the run was created |
| `endTime` | ISO 8601 or null | When the run completed |
| `gitBranch` | String | Branch used |
| `gitHash` | String | Commit hash used |
| `message` | String | Human-readable status message |

**Errors:**

| Status | Condition | Example message |
|--------|-----------|-----------------|
| `400` | Missing `repoUrl` | `"repoUrl must be provided"` |
| `400` | Missing both `pipelineName` and `pipelineYaml` | `"Either pipelineName or pipelineYaml must be provided"` |
| `400` | Pipeline not found in repo | `"Error finding pipeline: no file matches name 'xyz'"` |
| `400` | YAML parsing errors | `"YAML parsing errors: .pipelines/default.yaml:3:10: wrong type..."` |
| `400` | Validation errors | `"Validation errors: .pipelines/default.yaml:5:3: empty stage 'build'"` |
| `500` | I/O or unexpected error | `"Error processing pipeline: ..."` |
| `503` | RabbitMQ unavailable | `"Message queue unavailable: ..."` |

---

## Reports

### GET /pipelines/{name}/runs

List all runs for a pipeline.

**Path parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `name` | String | Pipeline name |

**Response** (`200 OK`):

```json
{
  "pipelineName": "default",
  "runs": [
    {
      "run-no": 1,
      "status": "SUCCESS",
      "git-repo": "https://github.com/org/repo.git",
      "git-branch": "main",
      "git-hash": "c3aefda",
      "trace-id": "abc123def456",
      "start": "2025-08-29T16:17:52-07:00",
      "end": "2025-08-29T16:21:32-07:00"
    }
  ]
}
```

**Errors:**

| Status | Condition |
|--------|-----------|
| `404` | Pipeline not found |

---

### GET /pipelines/{name}/runs/{runNo}

Get details of a specific pipeline run, including all stages.

**Path parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `name` | String | Pipeline name |
| `runNo` | Integer | Run number |

**Response** (`200 OK`):

```json
{
  "pipelineName": "default",
  "run-no": 1,
  "status": "SUCCESS",
  "git-repo": "https://github.com/org/repo.git",
  "git-branch": "main",
  "git-hash": "c3aefda",
  "trace-id": "abc123def456",
  "start": "2025-08-29T16:17:52-07:00",
  "end": "2025-08-29T16:24:32-07:00",
  "stages": [
    {
      "name": "build",
      "status": "SUCCESS",
      "start": "2025-08-29T16:18:05-07:00",
      "end": "2025-08-29T16:19:32-07:00"
    }
  ]
}
```

**Errors:**

| Status | Condition |
|--------|-----------|
| `404` | Pipeline or run not found |

---

### GET /pipelines/{name}/runs/{runNo}/stages/{stageName}

Get details of a specific stage, including all its jobs.

**Path parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `name` | String | Pipeline name |
| `runNo` | Integer | Run number |
| `stageName` | String | Stage name |

**Response** (`200 OK`):

```json
{
  "pipelineName": "default",
  "run-no": 1,
  "status": "SUCCESS",
  "git-repo": "https://github.com/org/repo.git",
  "git-branch": "main",
  "git-hash": "c3aefda",
  "trace-id": "abc123def456",
  "start": "2025-08-29T16:17:52-07:00",
  "end": "2025-08-29T16:24:32-07:00",
  "stages": [
    {
      "name": "build",
      "status": "SUCCESS",
      "start": "2025-08-29T16:18:05-07:00",
      "end": "2025-08-29T16:19:32-07:00",
      "jobs": [
        {
          "name": "compile",
          "status": "SUCCESS",
          "failures": false,
          "start": "2025-08-29T16:18:05-07:00",
          "end": "2025-08-29T16:19:32-07:00",
          "artifacts": []
        }
      ]
    }
  ]
}
```

**Errors:**

| Status | Condition |
|--------|-----------|
| `404` | Pipeline, run, or stage not found |

---

### GET /pipelines/{name}/runs/{runNo}/stages/{stageName}/jobs/{jobName}

Get details of a specific job, including artifacts.

**Path parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `name` | String | Pipeline name |
| `runNo` | Integer | Run number |
| `stageName` | String | Stage name |
| `jobName` | String | Job name |

**Response** (`200 OK`):

```json
{
  "pipelineName": "default",
  "run-no": 1,
  "status": "SUCCESS",
  "git-repo": "https://github.com/org/repo.git",
  "git-branch": "main",
  "git-hash": "c3aefda",
  "trace-id": "abc123def456",
  "start": "2025-08-29T16:17:52-07:00",
  "end": "2025-08-29T16:24:32-07:00",
  "stages": [
    {
      "name": "build",
      "status": "SUCCESS",
      "start": "2025-08-29T16:18:05-07:00",
      "end": "2025-08-29T16:19:32-07:00",
      "jobs": [
        {
          "name": "compile",
          "status": "SUCCESS",
          "failures": false,
          "start": "2025-08-29T16:18:05-07:00",
          "end": "2025-08-29T16:19:32-07:00",
          "artifacts": [
            {
              "pattern": "build/libs/*.jar",
              "location": "minio://cicd-artifacts/default/1/build/compile/libs/app.jar"
            }
          ]
        }
      ]
    }
  ]
}
```

**Errors:**

| Status | Condition |
|--------|-----------|
| `404` | Pipeline, run, stage, or job not found |

---

## Status

### GET /pipelines/status?repo={url}

Get the status of all pipeline runs for a repository.

**Query parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `repo` | String | Yes | Repository URL |

**Response** (`200 OK`): Array of pipeline run status objects.

```json
[
  {
    "pipeline-name": "default",
    "run-no": 1,
    "status": "SUCCESS",
    "git-repo": "https://github.com/org/repo.git",
    "git-branch": "main",
    "git-hash": "c3aefda",
    "start": "2025-08-29T16:17:52-07:00",
    "end": "2025-08-29T16:21:32-07:00",
    "stages": [
      {
        "name": "build",
        "status": "SUCCESS",
        "start": "2025-08-29T16:18:05-07:00",
        "end": "2025-08-29T16:19:32-07:00",
        "jobs": [
          {
            "name": "compile",
            "status": "SUCCESS",
            "start": "2025-08-29T16:18:05-07:00",
            "end": "2025-08-29T16:19:32-07:00"
          }
        ]
      }
    ]
  }
]
```

**Errors:**

| Status | Condition |
|--------|-----------|
| `404` | No runs found for repository |

---

### GET /pipelines/{name}/status?run={runNo}

Get the status of a specific pipeline run.

**Path parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `name` | String | Pipeline name |

**Query parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `run` | Integer | Yes | Run number |

**Response** (`200 OK`): Single pipeline status object (same structure as above, without the outer array).

**Errors:**

| Status | Condition |
|--------|-----------|
| `404` | Pipeline or run not found |

---

## Health & Metrics

### GET /actuator/health

Kubernetes liveness/readiness probe endpoint.

**Response** (`200 OK`):

```json
{
  "status": "UP"
}
```

### GET /actuator/prometheus

Prometheus metrics endpoint. Returns metrics in Prometheus text exposition format.

Custom metrics exposed:

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `cicd_pipeline_runs_total` | Counter | pipeline, status | Total pipeline runs |
| `cicd_pipeline_duration_seconds` | Timer | pipeline | Pipeline execution duration |
| `cicd_stage_duration_seconds` | Timer | pipeline, stage | Stage execution duration |
| `cicd_job_duration_seconds` | Timer | pipeline, stage, job | Job execution duration |
| `cicd_job_runs_total` | Counter | pipeline, stage, job, status | Total job runs |

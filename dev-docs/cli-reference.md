# CLI Reference

The `cicd` command line tool is the primary interface for interacting with the CI/CD system. Built with PicoCLI, all commands support `-h`/`--help` and `-V`/`--version`.

```
cicd [-hV] [COMMAND]
```

| Option | Description |
|--------|-------------|
| `-h`, `--help` | Show help message and exit |
| `-V`, `--version` | Print version information and exit |

## verify

Validate a pipeline YAML configuration file or all files in a directory.

```
cicd verify <file>
```

| Argument/Option | Type | Required | Description |
|-----------------|------|----------|-------------|
| `<file>` | Path | Yes | Path to a YAML file or a directory containing YAML files. Default: `.pipelines/default.yaml` |
| `-h`, `--help` | — | No | Show help message and exit |

**Behavior:**
- If given a **file path**, validates that single file in isolation (pipeline name uniqueness check is skipped).
- If given a **directory path**, validates all `.yaml`/`.yml` files in the directory and checks for duplicate pipeline names across files.

**Validation checks:**
- YAML syntax (parsing errors report file, line, column)
- Required keys (`pipeline.name`, `stages`, job `stage`, job `image`, job `script`)
- At least one stage defined
- No empty stages (every stage must have at least one job)
- Job `stage` references a defined stage
- `needs` lists are non-empty and reference jobs in the same stage
- No cyclic dependencies in `needs` within a stage

**Exit codes:**
- `0` — all files are valid
- `1` — one or more validation errors

**Error format:**
```
<filepath>:<line>:<column>: <error message>
```

**Examples:**
```bash
cicd verify .pipelines/default.yaml
cicd verify .pipelines/
```

---

## dryrun

Validate a pipeline file and print its execution order without running it.

```
cicd dryrun <file>
```

| Argument/Option | Type | Required | Description |
|-----------------|------|----------|-------------|
| `<file>` | Path | Yes | Path to the pipeline YAML configuration file |
| `-h`, `--help` | — | No | Show help message and exit |

**Behavior:**
- Parses and validates the pipeline file.
- If valid, computes the execution order (topological sort with wave grouping) and prints the plan as YAML.

**Exit codes:**
- `0` — valid pipeline, execution order printed
- `1` — validation errors (printed to stderr)

**Output format:**
```yaml
build:
    compile:
        image: gradle:8.12-jdk21
        script:
        - ./gradlew classes
        failures: false
test:
    unittests:
        image: gradle:8.12-jdk21
        script:
        - ./gradlew test
        failures: false
```

**Examples:**
```bash
cicd dryrun .pipelines/default.yaml
```

---

## run

Submit a pipeline for execution on the server. This command is asynchronous — it returns immediately with a run number.

```
cicd run [OPTIONS]
```

| Option | Type | Required | Description |
|--------|------|----------|-------------|
| `--name <string>` | String | One of `--name` or `--file` | Pipeline name to find in the repository's `.pipelines/` directory |
| `--file <filepath>` | Path | One of `--name` or `--file` | Path to a local pipeline YAML file. Its content is sent to the server |
| `--repo-url <url>` | String | No | Git repository URL. Default: auto-detected from current directory's `origin` remote |
| `--branch <string>` | String | No | Git branch to use |
| `--commit <string>` | String | No | Git commit hash to use |
| `--server <url>` | String | No | Server URL. Default: `http://localhost:8080` |
| `-h`, `--help` | — | No | Show help message and exit |

**Constraints:**
- `--name` and `--file` are mutually exclusive — specify one, not both.
- `--repo-url` is required if the current directory is not a Git repository with an `origin` remote.

**Exit codes:**
- `0` — pipeline execution queued successfully
- `1` — error (invalid options, file not found, server error)

**Output on success:**
```
Pipeline execution queued.
  Pipeline: default
  Run #: 1
  Status: PENDING

Check progress with:
  cicd report --pipeline default --run 1
```

**Examples:**
```bash
cicd run --name default
cicd run --file .pipelines/release.yaml
cicd run --name default --branch my-feature
cicd run --name default --repo-url https://github.com/org/repo.git --branch main --commit 61b1b60
```

---

## report

Query pipeline execution history from the server. Supports four levels of detail.

```
cicd report --pipeline <name> [OPTIONS]
```

| Option | Type | Required | Description |
|--------|------|----------|-------------|
| `--pipeline <name>` | String | Yes | Pipeline name |
| `--run <n>` | Integer | No | Run number |
| `--stage <name>` | String | No | Stage name (requires `--run`) |
| `--job <name>` | String | No | Job name (requires `--stage`) |
| `--server <url>` | String | No | Server URL. Default: `http://localhost:8080` |
| `-h`, `--help` | — | No | Show help message and exit |

**Query levels:**

| Level | Options | Returns |
|-------|---------|---------|
| 1 | `--pipeline` | All runs for the pipeline with summary info |
| 2 | `--pipeline --run` | Single run with all stages |
| 3 | `--pipeline --run --stage` | Single stage with all its jobs |
| 4 | `--pipeline --run --stage --job` | Single job with artifacts |

**Constraints:**
- `--stage` requires `--run`
- `--job` requires `--stage`

**Exit codes:**
- `0` — report printed
- `1` — not found or server error

**Examples:**
```bash
cicd report --pipeline default
cicd report --pipeline default --run 1
cicd report --pipeline default --run 1 --stage build
cicd report --pipeline default --run 1 --stage build --job compile
```

---

## status

Query the current status of pipeline runs.

```
cicd status [OPTIONS]
```

| Option | Type | Required | Description |
|--------|------|----------|-------------|
| `--repo <url>` | String | One of `--repo` or `--file` | Repository URL to query all runs |
| `-f`, `--file <path>` | Path | One of `--repo` or `--file` | Pipeline file path (extracts pipeline name from YAML) |
| `--run <n>` | Integer | No | Run number (requires `--file`) |
| `--server <url>` | String | No | Server URL. Default: `http://localhost:8080` |
| `-h`, `--help` | — | No | Show help message and exit |

**Constraints:**
- `--repo` and `--file` are mutually exclusive — specify one, not both.
- `--run` requires `--file`.

**Exit codes:**
- `0` — status printed
- `1` — not found, invalid YAML, or server error

**Examples:**
```bash
cicd status --repo https://github.com/org/repo.git
cicd status -f .pipelines/default.yaml --run 1
```

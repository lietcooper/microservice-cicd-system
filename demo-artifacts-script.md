# Artifacts Feature Demo Script

> Total time: ~5 minutes.
> One window: Terminal (demo-artifacts.sh).
> Deployed on Minikube via Helm.

---

## Part 1: Artifact Specification in YAML (~30s)

**Say:**
We added support for artifact uploads in our CI/CD system. Jobs can specify files, directories, and wildcard patterns to collect after execution.

**CLI:**
```
cat .pipelines/demo-success.yaml
```
- "This pipeline has two stages. The build job specifies two artifact patterns: an exact file `build/output.txt` and a wildcard `build/reports/*.html`. The test job uploads the entire `results/` directory."
- "Artifacts are only collected when a job succeeds."

---

## Part 2: Run Pipeline with Artifacts (~1min)

**CLI:**
```
cicd run --file .pipelines/demo-success.yaml --repo-url $REPO --server $SERVER
```
- "We trigger the pipeline. The worker will execute each job in Docker, then collect and upload any matching artifacts to MinIO."

```
cicd status ...
```
- "Both stages passed — artifacts should have been collected."

---

## Part 3: Artifacts in Report (~1.5min)

**Say:**
The report command now shows artifacts for each job — the original pattern and the storage location in MinIO.

**CLI:**
```
cicd report --pipeline demo-success --run N --stage build --server $SERVER
```
- "The hello job has two artifacts:"
  - "`build/output.txt` — exact match, stored at `demo-success/N/build/hello/build/output.txt`"
  - "`build/reports/*.html` — wildcard matched `summary.html`"
- "The storage path follows the convention: `pipeline/run/stage/job/relative-path`."

```
cicd report --pipeline demo-success --run N --stage test --server $SERVER
```
- "The check job collected `results/` — the directory pattern recursively uploaded all files inside."

---

## Part 4: Failed Pipeline — No Artifacts (~1min)

**Say:**
Artifacts are only uploaded when a job succeeds. Let's verify with a failing pipeline.

**CLI:**
```
cicd run --file .pipelines/demo-fail.yaml ...
```
- "This pipeline has a job that exits with code 1."

```
cicd status ...
```
- "Status: FAILED."

```
cicd report --pipeline demo-fail --run N --stage build --server $SERVER
```
- "No artifacts field — the job failed, so nothing was collected. This is the expected behavior per the spec."

---

## Part 5: Pattern Matching Examples (~30s)

**Say:**
The system supports the full pattern matching spec from the assignment.

Walk through the displayed table:
- "Exact file like `README.md`"
- "Directory with trailing slash like `build/` — collects everything recursively"
- "Suffix wildcard: `*.java` matches all Java files"
- "Prefix wildcard: `READ*` matches README, READLOG, etc."
- "Single-level directory wildcard: `build/*/doc/` matches `build/java/doc/` but not `build/a/b/doc/`"
- "Double-star: `build/**/distribution` matches at any depth"

---

## Part 6: Storage & Implementation (~30s)

**Say:**
Artifacts are stored in MinIO, which is an S3-compatible object storage deployed as a StatefulSet in our Helm chart.

**CLI:**
```
kubectl get pods -n cicd | grep minio
```
- "MinIO runs alongside the other components."

Walk through the flow:
1. "YAML defines the patterns"
2. "ArtifactCollectorService matches files in the workspace using Java glob"
3. "ArtifactStorageService uploads to MinIO"
4. "Metadata is persisted to a PostgreSQL `artifacts` table via the status update message"
5. "The report API returns the pattern and storage location for each artifact"

---

## Wrap-up (~5s)

**Say:**
That's the artifacts feature — YAML pattern specification, automatic collection on success, MinIO storage, and full report integration.

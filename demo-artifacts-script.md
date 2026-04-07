# Artifacts Feature Demo Script

> Total time: ~5 minutes.
> One window: Terminal (demo-artifacts.sh).
> Deployed on Minikube via Helm.

---

## Intro (~10s)

**Say:**
Hi professor, thanks for taking the time to watch our demo. Today we're going to walk you through the artifacts feature we added to our CI/CD system — it lets users specify which build outputs to keep, and the system automatically collects and stores them after a successful job.

---

## Part 1: Artifact Specification in YAML (~30s)

**Say:**
So let's start by looking at what artifact configuration looks like from the user's perspective.

**CLI:**
```
cat .pipelines/demo-success.yaml
```
- "Here's a pipeline with two stages — build and test. If you look at the hello job under build, you'll see the `artifacts` key. It lists two patterns: an exact file `build/output.txt`, and a wildcard `build/reports/*.html` to grab any HTML reports."
- "And down in the test stage, the check job uses `results/` with a trailing slash, which means it'll upload that entire directory recursively."
- "One important thing — artifacts only get collected when a job actually succeeds."

---

## Part 2: Run Pipeline with Artifacts (~1min)

**Say:**
Alright, let's actually run this and see it in action.

**CLI:**
```
cicd run --file .pipelines/demo-success.yaml --repo-url $REPO --server $SERVER
```
- "We're triggering the pipeline now. Behind the scenes, the worker picks up each job, runs it in a Docker container, and once it finishes successfully, it scans the workspace for files matching those artifact patterns and uploads them to MinIO."

```
cicd status ...
```
- "Great, both stages passed — so our artifacts should have been collected."

---

## Part 3: Artifacts in Report (~1.5min)

**Say:**
Now let's check the report to see if the artifacts actually show up.

**CLI:**
```
cicd report --pipeline demo-success --run N --stage build --server $SERVER
```
- "Here we go — the hello job has two artifacts listed. You can see the original pattern on the left and the storage location on the right."
- "So `build/output.txt` was an exact match, and it's stored at `demo-success/N/build/hello/build/output.txt` in MinIO."
- "And `build/reports/*.html` matched `summary.html` — the wildcard expanded to the actual file."
- "The storage path follows a consistent convention: pipeline name, run number, stage, job, then the relative path."

```
cicd report --pipeline demo-success --run N --stage test --server $SERVER
```
- "And here's the test stage — the check job used that `results/` directory pattern, and it picked up `test-result.txt` from inside it."

---

## Part 4: Failed Pipeline — No Artifacts (~1min)

**Say:**
Now let's make sure the other side works too — when a job fails, we should not see any artifacts.

**CLI:**
```
cicd run --file .pipelines/demo-fail.yaml ...
```
- "This pipeline has a job that deliberately exits with code 1."

```
cicd status ...
```
- "Yep, it failed as expected."

```
cicd report --pipeline demo-fail --run N --stage build --server $SERVER
```
- "And if we look at the report — no artifacts field at all. The job failed, so nothing was collected. That's exactly the behavior the spec calls for."

---

## Part 5: Pattern Matching Examples (~30s)

**Say:**
Let me quickly go over all the pattern types we support, since the spec had pretty specific requirements here.

Walk through the displayed table:
- "An exact file name like `README.md` — straightforward."
- "A directory with a trailing slash like `build/` — that grabs everything inside recursively."
- "Single star for wildcards — `*.java` matches all Java files, `READ*` matches anything starting with READ."
- "Single star in a path like `build/*/doc/` — matches directories exactly one level deep, so `build/java/doc/` works but `build/a/b/doc/` doesn't."
- "And double star like `build/**/distribution` — that matches at any depth, so it'll find distribution directories no matter how deeply nested they are."

---

## Part 6: Storage & Implementation (~30s)

**Say:**
Finally, let me briefly show you how this is set up on the infrastructure side.

**CLI:**
```
kubectl get pods -n cicd | grep minio
```
- "We're using MinIO as the artifact backend — it's S3-compatible and runs right here in the cluster as a StatefulSet with persistent storage."

Walk through the flow:
- "So the way it all fits together: the YAML defines the patterns, then after a job succeeds, our ArtifactCollectorService scans the workspace using Java's glob matching. Matched files get uploaded to MinIO by the ArtifactStorageService. The metadata — which pattern matched and where it's stored — gets sent back to the server through the status update message and persisted to a PostgreSQL artifacts table. And then the report API just reads that table and returns it."

---

## Wrap-up (~5s)

**Say:**
So that's the artifacts feature — pattern specification in YAML, automatic collection on success, MinIO storage, and it's all wired into the existing report command. Thanks for watching.

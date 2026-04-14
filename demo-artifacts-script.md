# Artifacts Feature Demo Script

> Total time: ~4 minutes.
> One window: Terminal (demo-artifacts.sh).
> Deployed on Minikube via Helm.

---

## Intro (~10s)

**Say:**
Hi professor, thanks for taking the time to watch our demo. Today we're showing the artifacts feature — it lets pipeline jobs specify which build outputs to keep, and the system automatically collects and stores them after a successful run.

---

## Part 1: Artifact Specification in YAML (~30s)

**Say:**
So let's start by looking at what this looks like from the user's side.

**CLI:**
```
cat .pipelines/demo-success.yaml
```
- "Here's a pipeline with two stages. The hello job under build has an `artifacts` key with two patterns — an exact file `build/output.txt` and a wildcard `build/reports/*.html`. The test job uses `results/` with a trailing slash, which means grab everything in that directory."
- "Artifacts only get collected when a job succeeds."

---

## Part 2: Run Pipeline with Artifacts (~1min)

**Say:**
Alright, let's run this and see what happens.

**CLI:**
```
cicd run --file .pipelines/demo-success.yaml --repo-url $REPO --server $SERVER
```
- "The worker picks up each job, runs it in Docker, and after it finishes successfully, it scans the workspace for matching files and uploads them to MinIO."

```
cicd status ...
```
- "Both stages passed, so our artifacts should be there."

---

## Part 3: Artifacts in Report (~1min)

**Say:**
Now let's check the report to see if they actually show up.

**CLI:**
```
cicd report --pipeline demo-success --run N --stage build --server $SERVER
```
- "Here we go — two artifacts on the hello job. Pattern on the left, storage location on the right."
- "`build/output.txt` was a direct match. `build/reports/*.html` expanded to `summary.html`."
- "Storage path follows: pipeline, run number, stage, job, then the file path."

```
cicd report --pipeline demo-success --run N --stage test --server $SERVER
```
- "And the check job picked up the `results/` directory with the test output inside."

---

## Part 4: Failed Pipeline — No Artifacts (~1min)

**Say:**
Now let's confirm the other case — when a job fails, no artifacts should be collected.

**CLI:**
```
cicd run --file .pipelines/demo-fail.yaml ...
```
- "This job exits with code 1 on purpose."

```
cicd status ...
```
- "Failed, as expected."

```
cicd report --pipeline demo-fail --run N --stage build --server $SERVER
```
- "No artifacts field at all. Job failed, nothing collected — that's the correct behavior."

---

## Part 5: Storage Backend (~30s)

**Say:**
Let's take a look at where the artifacts actually live.

**CLI:**
```
kubectl get pods -n cicd | grep minio
```
- "Artifacts are stored in MinIO — an S3-compatible object store running in the cluster."

```
kubectl exec -n cicd cicd-postgresql-0 -- psql -U cicd -d cicd -c 'SELECT ... FROM artifacts ...'
```
- "And here's the artifacts table in PostgreSQL — each row has the job run ID, the original pattern from the YAML, and the MinIO storage path. This is what the report command reads from."

---

## Wrap-up (~5s)

**Say:**
That's the artifacts feature — pattern spec in YAML, automatic collection on success, MinIO storage, and it's all visible through the report command. Thanks for watching.

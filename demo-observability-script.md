# Observability + Artifacts Demo Script

> Total time: ~8-10 minutes.
> Two windows: Terminal (demo-observability.sh) + Browser (Grafana).
> Grafana: http://localhost:3000 (admin / admin)
> Deployed on Minikube via Helm.

---

## Intro (~10s)

**Say:**
We're going to demo the observability stack and the new artifacts feature for our CI/CD system. Everything runs on Kubernetes via Minikube, deployed with a single Helm chart. The observability stack includes OpenTelemetry, Prometheus, Loki, Tempo, and Grafana. The artifacts feature lets jobs upload build outputs to MinIO storage.

---

## Part 0: Environment Overview (~30s)

**CLI:**
```
kubectl get pods -n cicd
```
- "Here are the 10 components — server, worker with Docker-in-Docker sidecar, PostgreSQL, RabbitMQ, MinIO for artifact storage, and five observability components: OTel Collector, Prometheus, Loki, Tempo, and Grafana."

```
tree helm/cicd/ -L 2
```
- "All defined in a single Helm chart — templates, values, and dashboard JSON files."

---

## Part 1: Trigger Pipelines (~1.5min)

**CLI:**
```
cat .pipelines/demo-success.yaml
```
- "This pipeline has two stages: build and test. Notice the `artifacts` key on each job — the build job uploads `build/output.txt` and any HTML reports, the test job uploads the entire `results/` directory."

```
cicd run --file .pipelines/demo-success.yaml --repo-url $REPO --server $SERVER
```
- "Let's run the success pipeline and wait for it to complete."

```
cicd status ...
```
- "All stages passed."

```
cicd run --file .pipelines/demo-fail.yaml ...
```
- "Now a pipeline that fails — the job exits with code 1, so no artifacts are uploaded."

```
cicd status ...
```
- "FAILED as expected."

---

## Part 2: Artifacts in Report (~1min)

**Say:**
When a job succeeds and has artifact patterns configured, the system collects matching files from the workspace and uploads them to MinIO.

**CLI:**
```
cicd report --pipeline demo-success --run N --stage build --server $SERVER
```
- "In the build stage, the hello job has two artifacts:"
  - "`build/output.txt` matched and stored at `demo-success/N/build/hello/build/output.txt`"
  - "`build/reports/*.html` matched the summary.html file"
- "The storage path follows the convention: pipeline/run/stage/job/relative-path in MinIO."

```
cicd report --pipeline demo-success --run N --stage test --server $SERVER
```
- "The test stage's check job collected the entire `results/` directory."

---

## Part 3: Report with trace-id (~30s)

**CLI:**
```
cicd report --pipeline demo-success --server $SERVER
```
- "Every run now has a unique trace-id. This links to the distributed trace in Tempo."

```
cicd report --pipeline demo-success --run N --server $SERVER
```
- "Detailed view shows trace-id, stages, and timing. We'll use this trace ID in Grafana."

---

## Part 4: Metrics Endpoint (~30s)

**CLI:**
```
curl -s localhost:8080/actuator/prometheus | grep cicd_ | grep -v bucket
```
- "Five custom metrics exposed via Spring Actuator:"
  - "`cicd_pipeline_runs_total` — counter by status"
  - "`cicd_pipeline_duration_seconds` — histogram"
  - "`cicd_stage_duration_seconds` — per-stage"
  - "`cicd_job_duration_seconds` — per-job"
  - "`cicd_job_runs_total` — job counter by status"
- "Prometheus scrapes this every 15 seconds."

---

## Part 5: Dashboard 1 — Pipeline Overview (~1.5min)

**Browser:** http://localhost:3000/d/cicd-pipeline-overview

Walk through panels:
1. **Pipeline Runs by Status** — total SUCCESS vs FAILED from Prometheus
2. **Pipeline Duration Over Time** — p95 duration trend
3. **Recent Runs Table** — queried from PostgreSQL, shows pipeline, run, branch, commit, status, trace-id
   - "Click a trace-id to jump to the Trace Explorer dashboard"

---

## Part 6: Dashboard 4 — Trace Explorer (~1.5min)

**Browser:** http://localhost:3000/d/cicd-trace-explorer

- Select pipeline and run number
- "Top level: pipeline span. Inside: stage spans. Inside each stage: job spans."
- "Each span shows duration — you can see where time is spent."
- Compare success vs failure run: "Failed run stops early — the stage terminates."

---

## Part 7: Dashboard 2 — Stage & Job Breakdown (~1min)

**Browser:** http://localhost:3000/d/cicd-stage-job-breakdown

- "Per-stage duration, per-job duration, job success/failure counts."

---

## Part 8: Dashboard 3 — Logs Viewer (~1min)

**Browser:** http://localhost:3000/d/cicd-logs-viewer

- Filter by pipeline, run, stage, job
- "The `source` dropdown switches between `system` logs and `job-container` Docker output."
- "All logs are structured JSON with traceId and spanId for correlation."

---

## Part 9: Configuration as Code (~30s)

**CLI:**
```
ls helm/cicd/dashboards/
```
- "Four dashboard JSON files — all provisioned as code, not manually created."

```
head -20 helm/cicd/templates/grafana-configmap.yaml
```
- "Datasource provisioning: Prometheus, Loki, Tempo, PostgreSQL."

```
head -30 helm/cicd/templates/grafana-dashboards-configmap.yaml
```
- "Dashboard files mounted into Grafana via ConfigMap."

---

## Part 10: Prometheus Alert Rules (~30s)

**CLI:**
```
curl -s localhost:9090/api/v1/rules | python3 -m json.tool | head -60
```
- "Three alert rules defined as code:"
  - "CicdPipelineConsecutiveFailures — 3+ failures in a row"
  - "CicdJobDurationHigh — job p95 > 10 minutes"
  - "CicdPipelineDurationHigh — pipeline p95 > 30 minutes"

---

## Wrap-up (~10s)

**Say:**
That's the full stack — artifacts uploaded to MinIO, metrics with Prometheus, structured logs with Loki, distributed tracing with Tempo, four Grafana dashboards provisioned as code, and alerting rules. Everything deployed on Minikube with a single `helm install` command.

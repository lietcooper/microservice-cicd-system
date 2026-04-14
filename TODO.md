# TODO.md — Artifacts Sprint: Deploy & Demo Checklist

> Previous sprint (observability: Phases 1-12) is fully complete.
> PR #115 (artifacts feature) is merged to `origin/main` by teammate.
> Remaining work: merge, Minikube deploy, end-to-end validation, demo preparation.

---

## Phase 1: Merge & Build

### 1.1 Get PR #115 changes onto working branch
- [ ] `git fetch origin`
- [ ] Merge `origin/main` into current branch: `git merge origin/main`
- [ ] Resolve any merge conflicts (expect conflicts in files both branches touched: `StatusUpdateListener`, `ReportService`, `worker/build.gradle`, `helm/` templates, etc.)
- [ ] Verify compilation: `./gradlew clean build -x test`
- [ ] Run full tests: `./gradlew clean build`
  - Expected: 320+ tests pass (including new artifact tests from PR #115)

---

## Phase 2: Minikube Setup

### 2.1 Install & start Minikube
- [ ] Install Minikube if not present: `brew install minikube`
- [ ] Start cluster with sufficient resources:
  ```bash
  minikube start --cpus=4 --memory=8192 --driver=docker
  ```
  - Needs ~8GB RAM for full stack (server, worker, PostgreSQL, RabbitMQ, MinIO, OTel Collector, Prometheus, Loki, Tempo, Grafana)
- [ ] Verify: `kubectl get nodes` shows Ready

### 2.2 Build & load Docker images
- [ ] Build images:
  ```bash
  docker build --target server -t cicd-server:latest .
  docker build --target worker -t cicd-worker:latest .
  ```
- [ ] Load into Minikube:
  ```bash
  minikube image load cicd-server:latest
  minikube image load cicd-worker:latest
  ```
- [ ] Update `helm/cicd/values.yaml` if needed:
  - `server.image.repository: cicd-server` (local, no registry prefix)
  - `worker.image.repository: cicd-worker`
  - `*.image.pullPolicy: IfNotPresent` (for local images, avoid pulling from registry)

---

## Phase 3: Helm Deploy

### 3.1 Validate Helm chart
- [ ] Lint: `helm lint ./helm/cicd`
- [ ] Template dry-run: `helm template cicd ./helm/cicd` — renders without errors

### 3.2 Deploy to Minikube
- [ ] Install:
  ```bash
  helm install cicd ./helm/cicd -n cicd --create-namespace
  ```
- [ ] Watch pods come up: `kubectl get pods -n cicd -w`
- [ ] All pods Running/Ready:
  - [ ] `cicd-cicd-server` (1/1)
  - [ ] `cicd-cicd-worker` (2/2 — app + DinD sidecar)
  - [ ] `cicd-cicd-postgresql` (1/1)
  - [ ] `cicd-cicd-rabbitmq` (1/1)
  - [ ] `cicd-cicd-minio` (1/1)
  - [ ] `cicd-cicd-otel-collector` (1/1)
  - [ ] `cicd-cicd-prometheus` (1/1)
  - [ ] `cicd-cicd-loki` (1/1)
  - [ ] `cicd-cicd-tempo` (1/1)
  - [ ] `cicd-cicd-grafana` (1/1)
- [ ] Check server health: `kubectl port-forward svc/cicd-cicd-server 8080:8080 -n cicd` then `curl localhost:8080/actuator/health`

### 3.3 Troubleshoot if needed
- If pods crash: `kubectl describe pod <name> -n cicd` and `kubectl logs <name> -n cicd`
- If PVC issues: `kubectl get pvc -n cicd` — Minikube uses `standard` StorageClass by default
- If image pull errors: ensure `pullPolicy: IfNotPresent` and images are loaded
- If MinIO bucket creation fails on startup: check MinIO pod logs and worker logs for connection errors

---

## Phase 4: End-to-End Validation — Artifacts

### 4.1 Test artifacts with demo-success pipeline
- [ ] Port-forward server: `kubectl port-forward svc/cicd-cicd-server 8080:8080 -n cicd`
- [ ] Run pipeline with artifacts:
  ```bash
  java -jar cli/build/libs/cli-0.1.0.jar run \
    --file .pipelines/demo-success.yaml \
    --repo-url https://github.com/lietcooper/fail-demo-repo \
    --server http://localhost:8080
  ```
- [ ] Wait for completion, check status:
  ```bash
  java -jar cli/build/libs/cli-0.1.0.jar status \
    --file .pipelines/demo-success.yaml --run N \
    --server http://localhost:8080
  ```
- [ ] Verify report includes artifacts:
  ```bash
  java -jar cli/build/libs/cli-0.1.0.jar report \
    --pipeline demo-success --run N \
    --server http://localhost:8080
  ```
  - Should show artifact patterns and download locations for each job

### 4.2 Test failed pipeline (no artifacts expected)
- [ ] Run demo-fail pipeline
- [ ] Verify: status is FAILED, no artifacts uploaded (artifacts only upload on job success)

### 4.3 Test observability still works
- [ ] Metrics: `curl localhost:8080/actuator/prometheus | grep cicd_`
- [ ] Report trace-id: `cicd report --pipeline demo-success --run N` shows `trace-id` field
- [ ] Port-forward Grafana: `kubectl port-forward svc/cicd-cicd-grafana 3000:3000 -n cicd`
  - [ ] Dashboard 1 (Pipeline Overview) shows runs
  - [ ] Dashboard 4 (Trace Explorer) shows span hierarchy
  - [ ] Dashboard 3 (Logs Viewer) shows logs
  - [ ] Dashboard 2 (Stage & Job Breakdown) shows metrics

---

## Phase 5: Demo Preparation

### 5.1 Update demo script for Minikube + artifacts
- [ ] Update `demo-observability.sh` (or create new `demo-artifacts.sh`):
  - Setup phase: `minikube start`, port-forwards, pre-seed data
  - Part 1: Trigger demo-success (with artifacts) and demo-fail
  - Part 2: Show report with artifacts + trace-id
  - Part 3: Show metrics endpoint
  - Part 4-7: Grafana dashboards (browser)
  - Part 8: Show artifact patterns in YAML (`cat .pipelines/demo-success.yaml`)
  - Part 9: Configuration as code (Helm chart structure, dashboards)
  - Part 10: Alert rules
- [ ] Ensure `SERVER` var uses `http://localhost:8080` (port-forwarded)
- [ ] Make sure demo works with Minikube (not EC2 IP)

### 5.2 Update demo script (talk track)
- [ ] Update `demo-observability-script.md` to include artifacts demo points:
  - Show artifact patterns in pipeline YAML
  - Show `cicd report` output with artifact download locations
  - Explain MinIO storage (S3-compatible, deployed in K8s)
  - Link artifacts to observability (traces show artifact collection timing)

### 5.3 Dry run
- [ ] Run full demo end-to-end on Minikube
- [ ] Verify all CLI commands produce expected output
- [ ] Verify all Grafana dashboards load correctly
- [ ] Time the demo (target: ~8 minutes)

---

## Phase 6: Final Cleanup

- [ ] Ensure `./gradlew clean build` passes (all tests green)
- [ ] Ensure `helm lint ./helm/cicd` clean
- [ ] Remove any hardcoded EC2 IPs from scripts (replace with `localhost`)
- [ ] Commit and push all changes

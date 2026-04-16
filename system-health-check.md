# System Health Check

This document explains how to verify that the current Kubernetes deployment is healthy after installing the Helm chart.

The checks below assume the stack was deployed with:

```bash
helm upgrade --install cicd ./helm/cicd -n cicd --create-namespace
```

The examples below assume:
- namespace: `cicd`
- Helm release: `cicd`

If you used different names, replace them in the commands.

## Prerequisites

- `kubectl` is installed and configured for the target cluster
- `helm` is installed
- The Helm release has been installed successfully
- The CLI launcher script is available as `./cicd` or `cicd`

## 1. Verify Kubernetes Access

```bash
kubectl get ns
kubectl config current-context
helm list -n cicd
```

Expected result:
- the cluster is reachable
- namespace `cicd` exists
- Helm release `cicd` appears in namespace `cicd`

## 2. Verify Workloads

Check Deployments and StatefulSets:

```bash
kubectl get deploy,statefulset -n cicd
```

Expected result:
- Deployment `cicd-cicd-server` is ready
- Deployment `cicd-cicd-worker` is ready
- StatefulSets for PostgreSQL, RabbitMQ, and MinIO are ready if enabled
- observability workloads are ready if enabled

Check Pods:

```bash
kubectl get pods -n cicd
```

Expected result:
- server Pod is `Running`
- worker Pod is `Running`
- PostgreSQL, RabbitMQ, and MinIO Pods are `Running` if enabled
- Prometheus, Grafana, Loki, Tempo, and OTel Collector Pods are `Running` if enabled
- Pods are not stuck in `CrashLoopBackOff`, `ImagePullBackOff`, or `Pending`

## 3. Verify Services

```bash
kubectl get svc -n cicd
```

Expected result:
- `cicd-cicd-server` exists
- `cicd-cicd-worker` exists
- services for enabled dependencies exist

## 4. Verify Server Health

Port-forward the server in a separate terminal:

```bash
kubectl port-forward svc/cicd-cicd-server 8080:8080 -n cicd
```

Then run:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/pipelines/default/runs
```

Expected result:
- `/actuator/health` returns `UP`
- `/pipelines/default/runs` returns JSON
- if no runs exist yet, the runs list may be empty

If the server is not healthy, inspect:

```bash
kubectl logs deployment/cicd-cicd-server -n cicd
kubectl describe deployment cicd-cicd-server -n cicd
```

## 5. Verify Worker Health

Port-forward the worker in a separate terminal:

```bash
kubectl port-forward svc/cicd-cicd-worker 8081:8081 -n cicd
```

Then run:

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8081/actuator/prometheus | head
```

Expected result:
- `/actuator/health` returns `UP`
- `/actuator/prometheus` returns metrics text

If the worker is not healthy, inspect:

```bash
kubectl logs deployment/cicd-cicd-worker -n cicd -c worker
kubectl logs deployment/cicd-cicd-worker -n cicd -c dind
kubectl describe deployment cicd-cicd-worker -n cicd
```

## 6. Verify RabbitMQ

Check the workload:

```bash
kubectl get pods -n cicd | grep rabbitmq
```

Port-forward the management UI:

```bash
kubectl port-forward svc/cicd-cicd-rabbitmq 15672:15672 -n cicd
```

RabbitMQ management UI:
- URL: `http://localhost:15672`
- Username: `cicd`
- Password: `cicd`

Expected result:
- RabbitMQ Pod is running
- the management UI is reachable
- after pipeline activity, queues such as `cicd.pipeline.execute`, `cicd.job.execute`, `cicd.job.results`, and `cicd.status.update` are visible

## 7. Verify PostgreSQL

Check the workload:

```bash
kubectl get pods -n cicd | grep postgresql
```

Run a readiness check inside the Pod:

```bash
kubectl exec -n cicd statefulset/cicd-cicd-postgresql -- pg_isready -U cicd
```

Expected result:
- PostgreSQL reports that it is accepting connections

Optional table check:

```bash
kubectl exec -n cicd statefulset/cicd-cicd-postgresql -- psql -U cicd -d cicd -c "\dt"
```

Expected result:
- tables such as `pipeline_runs`, `stage_runs`, `job_runs`, and artifact-related tables exist

## 8. Verify MinIO

Check the workload:

```bash
kubectl get pods -n cicd | grep minio
```

Port-forward the MinIO Console:

```bash
kubectl port-forward svc/cicd-cicd-minio 9001:9001 -n cicd
```

MinIO Console:
- URL: `http://localhost:9001`
- Username: `minioadmin`
- Password: `minioadmin`

Expected result:
- MinIO Pod is running
- the MinIO console is reachable
- the configured artifact bucket exists after the app initializes or after artifact-producing jobs run

## 9. Verify Observability Stack

If observability is enabled, run the validation script:

```bash
./validate-observability.sh
```

Expected result:
- the script reports passing checks for pod health, actuator endpoints, Prometheus, Grafana, Loki, and Tempo

You can also verify services manually:

```bash
kubectl port-forward svc/cicd-cicd-prometheus 9090:9090 -n cicd
kubectl port-forward svc/cicd-cicd-grafana 3000:3000 -n cicd
kubectl port-forward svc/cicd-cicd-loki 3100:3100 -n cicd
kubectl port-forward svc/cicd-cicd-tempo 3200:3200 -n cicd
```

## 10. Verify the CLI

If the launcher script is in `PATH`:

```bash
cicd --help
```

Otherwise:

```bash
./cicd --help
```

Expected result:
- the CLI help text is displayed
- no Docker or image pull error is shown

For pipeline commands, keep the server port-forward active and use `--server` if needed:

```bash
./cicd report --pipeline default --server http://localhost:8080
```

## 11. Successful Pipeline Smoke Test

Keep the server port-forward active, then run:

```bash
./cicd run --repo-url https://github.com/lietcooper/success-demo-repo.git --name default --branch main --server http://localhost:8080
```

Expected result:
- the CLI reports that the pipeline execution was queued
- a pipeline name and run number are printed

Then inspect the report:

```bash
./cicd report --pipeline default --server http://localhost:8080
./cicd report --pipeline default --run 1 --server http://localhost:8080
./cicd report --pipeline default --run 1 --stage build --server http://localhost:8080
./cicd report --pipeline default --run 1 --stage build --job compile --server http://localhost:8080
```

Expected result:
- a run appears for pipeline `default`
- the run eventually becomes `SUCCESS`
- stage and job detail endpoints return data

If the demo pipeline produces artifacts, the job-level report should include artifact entries with `minio://...` locations.

## 12. Failing Pipeline Smoke Test

Keep the server port-forward active, then run:

```bash
./cicd run --repo-url https://github.com/lietcooper/fail-demo-repo.git --name default --branch main --server http://localhost:8080
```

Then inspect the report:

```bash
./cicd report --pipeline default --server http://localhost:8080
```

Expected result:
- a new run appears
- the run eventually becomes `FAILED`

## 13. Optional Status Check

Repository-level status:

```bash
./cicd status --repo https://github.com/lietcooper/success-demo-repo.git --server http://localhost:8080
```

Run-level status from a local pipeline file:

```bash
./cicd status --file .pipelines/default.yaml --run 1 --server http://localhost:8080
```

Expected result:
- the CLI prints current stage and job status for the matching run

## 14. What a Healthy Kubernetes Deployment Looks Like

A healthy deployment should satisfy all of the following:
- the Helm release is deployed successfully
- required Deployments and StatefulSets are ready
- Pods are running without restart loops
- server and worker `/actuator/health` endpoints return `UP`
- RabbitMQ is reachable and queues are active
- PostgreSQL accepts connections
- MinIO is reachable
- observability services are reachable if enabled
- a successful example pipeline completes
- a failing example pipeline is reported correctly

## 15. Common Failure Indicators

| Symptom | Likely Cause |
|---------|--------------|
| Helm release is `failed` | invalid chart values, image pull issue, or dependency startup failure |
| Pod is `ImagePullBackOff` | image repository, tag, or registry credentials are wrong |
| Pod is `CrashLoopBackOff` | application startup failure, bad config, or dependency connectivity problem |
| server `/actuator/health` is not `UP` | PostgreSQL or RabbitMQ connectivity issue |
| worker `/actuator/health` is not `UP` | RabbitMQ, MinIO, or Docker-in-Docker issue |
| `./cicd run` stays `PENDING` | worker not consuming jobs, RabbitMQ problem, or queue consumer failure |
| `./cicd run` fails immediately | repository URL, branch, commit, pipeline name, or pipeline YAML is invalid |
| `./cicd report` shows no runs | execution request was not accepted, persisted, or the wrong server endpoint was used |
| artifact list is empty when artifacts are expected | MinIO unavailable, bucket/config mismatch, or job did not produce matching files |

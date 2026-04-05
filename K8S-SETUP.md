# Kubernetes Deployment Guide

This guide covers deploying the CI/CD system to Kubernetes using the Helm chart in `helm/cicd/`.

> The existing `docker-compose.yaml` setup is unchanged. Both deployment methods coexist.

---

## Component K8s-Enablement Declaration

| Component | Type | K8s Enabled | K8s Requirement | Helm Toggle | Notes |
|-----------|------|:-----------:|:---------------:|-------------|-------|
| **Server** | REST API | Yes | **Required** | Always deployed | Stateless Spring Boot service |
| **Worker** | Job executor | Yes | **Required** | Always deployed | Stateless, uses Docker-in-Docker sidecar |
| **CLI** | Command-line | No | N/A | N/A | Runs on developer machine, talks to server via HTTP |
| **PostgreSQL** | Database | Yes | **Optional** | `postgresql.enabled` | Stateful; can use external instance instead |
| **RabbitMQ** | Message queue | Yes | **Optional** | `rabbitmq.enabled` | Stateful; can use external instance instead |
| **OTel Collector** | Telemetry hub | Yes | Optional | `otelCollector.enabled` | Stateless |
| **Prometheus** | Metrics store | Yes | Optional | `prometheus.enabled` | Stateful (PVC) |
| **Loki** | Log aggregation | Yes | Optional | `loki.enabled` | Stateful (PVC) |
| **Tempo** | Trace storage | Yes | Optional | `tempo.enabled` | Stateful (PVC) |
| **Grafana** | Dashboards | Yes | Optional | `grafana.enabled` | Stateless |

**Rule:** All stateless components (Server, Worker) are **required** to run in K8s. All stateful components (PostgreSQL, RabbitMQ) are **optionally** deployed in K8s and can be replaced with external instances.

---

## Architecture

```
┌─────────────────── Kubernetes Cluster ───────────────────────────┐
│                                                                   │
│  ┌──────────┐   ┌──────────┐                                     │
│  │  Server   │   │  Worker   │  ← Stateless (always in K8s)      │
│  │ :8080     │   │ :8081     │                                    │
│  │ REST API  │   │ + DinD    │                                    │
│  └────┬──────┘   └────┬──────┘                                    │
│       │               │                                           │
│       ▼               ▼                                           │
│  ┌──────────┐   ┌──────────┐                                     │
│  │PostgreSQL │   │ RabbitMQ │  ← Stateful (optional in K8s)      │
│  │ :5432     │   │ :5672    │    Can use external instances       │
│  └──────────┘   └──────────┘                                     │
│                                                                   │
│  ┌──────────────── Observability (optional) ──────────────────┐  │
│  │  OTel Collector → Prometheus / Loki / Tempo → Grafana      │  │
│  └────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────┘

        ▲
        │ HTTP (port-forward or NodePort/LoadBalancer)
        │
   ┌────┴─────┐
   │   CLI    │  ← Runs outside K8s on developer machine
   │ (cicd)   │
   └──────────┘
```

---

## Prerequisites

- Kubernetes cluster ([minikube](https://minikube.sigs.k8s.io/), [kind](https://kind.sigs.k8s.io/), or cloud-managed)
- [Helm 3](https://helm.sh/docs/intro/install/)
- Docker or equivalent container tooling (only needed if building images locally; skip if using pre-built images from a registry)

---

## Build Docker Images

```bash
docker build --target server -t cicd-server:latest .
docker build --target worker -t cicd-worker:latest .
```

Load into local cluster:

```bash
# minikube
minikube image load cicd-server:latest
minikube image load cicd-worker:latest

# kind
kind load docker-image cicd-server:latest
kind load docker-image cicd-worker:latest
```

For remote registries:

```bash
docker buildx build --platform linux/amd64 --target server -t <registry>/cicd-server:latest --push .
docker buildx build --platform linux/amd64 --target worker -t <registry>/cicd-worker:latest --push .
```

---

## Deployment Option 1: All-in-K8s (bundled PostgreSQL + RabbitMQ)

Deploy everything inside the cluster with defaults:

```bash
helm install cicd ./helm/cicd -n cicd --create-namespace
```

This deploys:
- `cicd-server` — Spring Boot REST API (Deployment)
- `cicd-worker` — RabbitMQ consumer + Docker-in-Docker sidecar (Deployment)
- `cicd-postgresql` — PostgreSQL 16 (StatefulSet, 5Gi PVC)
- `cicd-rabbitmq` — RabbitMQ 3.13 (StatefulSet, 2Gi PVC)
- Observability stack (OTel Collector, Prometheus, Loki, Tempo, Grafana)

Check status:

```bash
kubectl get pods -n cicd
kubectl get services -n cicd
```

---

## Deployment Option 2: Mixed Mode (external PostgreSQL and/or RabbitMQ)

Run stateful components outside K8s (e.g., via docker-compose, AWS RDS, CloudAMQP) while keeping server and worker in K8s.

### Start external services locally

```bash
# Start only PostgreSQL and RabbitMQ via docker-compose
docker-compose up -d
```

This starts PostgreSQL on `localhost:5432` and RabbitMQ on `localhost:5672`.

### Deploy to K8s with external services

Create `external-values.yaml`:

```yaml
# Disable bundled stateful components
postgresql:
  enabled: false
  externalUrl: "jdbc:postgresql://<host>:5432/cicd"
  externalUsername: cicd
  externalPassword: "cicd"

rabbitmq:
  enabled: false
  externalHost: "<host>"
  externalPort: 5672
  externalUsername: cicd
  externalPassword: "cicd"
```

Replace `<host>` with:
- **minikube:** `host.minikube.internal` (auto-resolves to host machine)
- **kind:** `host.docker.internal` or the host IP from `ip route` inside a pod
- **Cloud:** the managed service endpoint (e.g., RDS hostname, CloudAMQP URL)

Deploy:

```bash
helm install cicd ./helm/cicd -n cicd --create-namespace -f external-values.yaml
```

### External PostgreSQL only

```yaml
postgresql:
  enabled: false
  externalUrl: "jdbc:postgresql://my-db.example.com:5432/cicd"
  externalUsername: cicd
  externalPassword: "s3cr3t"

rabbitmq:
  enabled: true  # keep RabbitMQ in K8s
```

### External RabbitMQ only

```yaml
postgresql:
  enabled: true  # keep PostgreSQL in K8s

rabbitmq:
  enabled: false
  externalHost: "my-rmq.example.com"
  externalPort: 5672
  externalUsername: cicd
  externalPassword: "s3cr3t"
```

---

## Communication Between K8s and External Components

| Direction | How | Example |
|-----------|-----|---------|
| K8s pod → external DB | Set `postgresql.externalUrl` in values | `jdbc:postgresql://host.minikube.internal:5432/cicd` |
| K8s pod → external MQ | Set `rabbitmq.externalHost` in values | `host.minikube.internal` |
| CLI → K8s server | `kubectl port-forward` or NodePort/LoadBalancer | `kubectl port-forward svc/cicd-cicd-server 8080:8080` |
| External → K8s service | Change `server.service.type` to `NodePort` or `LoadBalancer` | `--set server.service.type=NodePort` |

The Helm chart's `_helpers.tpl` automatically selects internal K8s service URLs when bundled components are enabled, or external URLs when they are disabled.

---

## Accessing Services

### CLI to Server

```bash
# Port-forward (default ClusterIP)
kubectl port-forward svc/cicd-cicd-server 8080:8080 -n cicd

# Then use CLI normally
./gradlew :cli:run --args="run --name default --server http://localhost:8080"
./gradlew :cli:run --args="report --pipeline default --server http://localhost:8080"
```

### Grafana

```bash
kubectl port-forward svc/cicd-cicd-grafana 3000:3000 -n cicd
# Open http://localhost:3000 (admin / admin)
```

### Other observability services

```bash
kubectl port-forward svc/cicd-cicd-prometheus 9090:9090 -n cicd
kubectl port-forward svc/cicd-cicd-loki 3100:3100 -n cicd
kubectl port-forward svc/cicd-cicd-tempo 3200:3200 -n cicd
```

---

## Observability Stack

All observability components are optional and deployed by default.

| Component | Template | Purpose | Port |
|-----------|----------|---------|------|
| OTel Collector | `otel-collector-deployment.yaml` | Central OTLP ingestion, fans out to backends | 4317, 4318 |
| Prometheus | `prometheus-statefulset.yaml` | Metrics storage, scrapes server + worker | 9090 |
| Loki | `loki-statefulset.yaml` | Structured log aggregation | 3100 |
| Tempo | `tempo-statefulset.yaml` | Distributed trace storage | 3200 |
| Grafana | `grafana-deployment.yaml` | 4 pre-built dashboards, 4 datasources | 3000 |

Disable the entire stack:

```yaml
otelCollector:
  enabled: false
prometheus:
  enabled: false
loki:
  enabled: false
tempo:
  enabled: false
grafana:
  enabled: false
```

---

## Configuration Reference

| Key | Default | Description |
|-----|---------|-------------|
| `server.image.repository` | `yskigpg/cicd-server` | Server Docker image |
| `server.image.tag` | `latest` | Server image tag |
| `server.replicas` | `1` | Number of server pods |
| `server.service.type` | `ClusterIP` | Server Service type |
| `worker.image.repository` | `yskigpg/cicd-worker` | Worker Docker image |
| `worker.image.tag` | `latest` | Worker image tag |
| `worker.replicas` | `1` | Number of worker pods |
| `postgresql.enabled` | `true` | Deploy bundled PostgreSQL |
| `postgresql.database` | `cicd` | Database name |
| `postgresql.username` | `cicd` | DB username |
| `postgresql.password` | `cicd` | DB password |
| `postgresql.storage` | `5Gi` | PVC size |
| `postgresql.externalUrl` | `""` | JDBC URL when `enabled: false` |
| `postgresql.externalUsername` | `cicd` | External DB username |
| `postgresql.externalPassword` | `""` | External DB password |
| `rabbitmq.enabled` | `true` | Deploy bundled RabbitMQ |
| `rabbitmq.username` | `cicd` | RabbitMQ username |
| `rabbitmq.password` | `cicd` | RabbitMQ password |
| `rabbitmq.storage` | `2Gi` | PVC size |
| `rabbitmq.externalHost` | `""` | RabbitMQ host when `enabled: false` |
| `rabbitmq.externalPort` | `5672` | External RabbitMQ port |

Override at install time:

```bash
helm install cicd ./helm/cicd \
  --set server.image.tag=v1.2.3 \
  --set postgresql.password=strongpassword
```

---

## Upgrade / Uninstall

```bash
# Upgrade
helm upgrade cicd ./helm/cicd -n cicd -f my-values.yaml

# Uninstall
helm uninstall cicd -n cicd

# PVCs are not deleted automatically
kubectl delete pvc -l app.kubernetes.io/instance=cicd -n cicd
```

---

## Validation

```bash
# Verify Helm renders correctly
helm template cicd ./helm/cicd

# Verify mixed mode renders (no bundled DB/MQ)
helm template cicd ./helm/cicd \
  --set postgresql.enabled=false \
  --set rabbitmq.enabled=false

# Run the validation script after deployment
./validate-observability.sh
```

---

## File Structure

```
helm/cicd/
├── Chart.yaml
├── values.yaml
├── dashboards/
│   ├── pipeline-overview.json
│   ├── stage-job-breakdown.json
│   ├── logs-viewer.json
│   └── trace-explorer.json
└── templates/
    ├── _helpers.tpl
    ├── configmap.yaml
    ├── secret.yaml
    ├── server-deployment.yaml
    ├── server-service.yaml
    ├── worker-deployment.yaml
    ├── worker-service.yaml
    ├── postgresql-statefulset.yaml
    ├── rabbitmq-statefulset.yaml
    ├── otel-collector-deployment.yaml
    ├── otel-collector-configmap.yaml
    ├── prometheus-statefulset.yaml
    ├── prometheus-configmap.yaml
    ├── prometheus-rules-configmap.yaml
    ├── loki-statefulset.yaml
    ├── loki-configmap.yaml
    ├── tempo-statefulset.yaml
    ├── tempo-configmap.yaml
    ├── grafana-deployment.yaml
    ├── grafana-configmap.yaml
    └── grafana-dashboards-configmap.yaml
```

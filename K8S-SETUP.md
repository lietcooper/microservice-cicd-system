# Kubernetes Deployment Guide (Helm)

This guide covers deploying the CI/CD system to Kubernetes using the Helm chart in `helm/cicd/`.

> **Note:** The existing `docker-compose.yaml` setup is unchanged. Both deployment methods coexist.

---

## Prerequisites

- Kubernetes cluster (local: [minikube](https://minikube.sigs.k8s.io/) or [kind](https://kind.sigs.k8s.io/))
- [Helm 3](https://helm.sh/docs/intro/install/) installed
- Docker images built and accessible from the cluster

---

## Build Docker Images

```bash
# Build server image
docker build --target server -t cicd-server:latest .

# Build worker image
docker build --target worker -t cicd-worker:latest .
```

If using **minikube**, load the images directly:
```bash
minikube image load cicd-server:latest
minikube image load cicd-worker:latest
```

If using **kind**:
```bash
kind load docker-image cicd-server:latest
kind load docker-image cicd-worker:latest
```

---

## Quick Start (all-in-one with bundled PostgreSQL + RabbitMQ)

```bash
helm install cicd ./helm/cicd
```

This deploys:
- `cicd-server` — Spring Boot REST API on port 8080
- `cicd-worker` — Spring Boot RabbitMQ consumer on port 8081
- `cicd-postgresql` — PostgreSQL 16 StatefulSet
- `cicd-rabbitmq` — RabbitMQ 3.13 StatefulSet

Check status:
```bash
kubectl get pods
kubectl get services
```

---

## Accessing the Server

By default the server Service is `ClusterIP`. Options:

```bash
# Port-forward to localhost
kubectl port-forward svc/cicd-cicd-server 8080:8080

# Or change service type in values:
# server.service.type: NodePort
```

---

## Using External PostgreSQL / RabbitMQ

### External PostgreSQL only

```yaml
# my-values.yaml
postgresql:
  enabled: false
  externalUrl: "jdbc:postgresql://my-db.example.com:5432/cicd"
  externalUsername: cicd
  externalPassword: "s3cr3t"
```

### External RabbitMQ only

```yaml
rabbitmq:
  enabled: false
  externalHost: "my-rmq.example.com"
  externalPort: 5672
  externalUsername: cicd
  externalPassword: "s3cr3t"
```

Deploy with overrides:
```bash
helm install cicd ./helm/cicd -f my-values.yaml
```

---

## Configuration Reference

| Key | Default | Description |
|-----|---------|-------------|
| `server.image.repository` | `cicd-server` | Server Docker image |
| `server.image.tag` | `latest` | Server image tag |
| `server.replicas` | `1` | Number of server pods |
| `server.service.type` | `ClusterIP` | Server Service type |
| `worker.image.repository` | `cicd-worker` | Worker Docker image |
| `worker.image.tag` | `latest` | Worker image tag |
| `worker.replicas` | `1` | Number of worker pods |
| `postgresql.enabled` | `true` | Deploy bundled PostgreSQL |
| `postgresql.database` | `cicd` | Database name |
| `postgresql.username` | `cicd` | DB username |
| `postgresql.password` | `cicd` | DB password |
| `postgresql.storage` | `5Gi` | PVC size for PostgreSQL |
| `postgresql.externalUrl` | `""` | JDBC URL when `enabled: false` |
| `rabbitmq.enabled` | `true` | Deploy bundled RabbitMQ |
| `rabbitmq.username` | `cicd` | RabbitMQ username |
| `rabbitmq.password` | `cicd` | RabbitMQ password |
| `rabbitmq.storage` | `2Gi` | PVC size for RabbitMQ |
| `rabbitmq.externalHost` | `""` | RabbitMQ host when `enabled: false` |

Override any value at install time:
```bash
helm install cicd ./helm/cicd \
  --set server.image.tag=v1.2.3 \
  --set postgresql.password=strongpassword
```

---

## Upgrade

```bash
helm upgrade cicd ./helm/cicd -f my-values.yaml
```

## Uninstall

```bash
helm uninstall cicd
# PersistentVolumeClaims are NOT deleted automatically — remove manually if needed:
kubectl delete pvc -l app.kubernetes.io/instance=cicd
```

---

## File Structure

```
helm/cicd/
├── Chart.yaml                          # Chart metadata
├── values.yaml                         # Default values
└── templates/
    ├── _helpers.tpl                    # Template helpers
    ├── configmap.yaml                  # Non-secret env vars
    ├── secret.yaml                     # Passwords (base64)
    ├── server-deployment.yaml          # Server Deployment
    ├── server-service.yaml             # Server Service
    ├── worker-deployment.yaml          # Worker Deployment
    ├── worker-service.yaml             # Worker Service
    ├── postgresql-statefulset.yaml     # PostgreSQL (optional)
    └── rabbitmq-statefulset.yaml       # RabbitMQ (optional)
```

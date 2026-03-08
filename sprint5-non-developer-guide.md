# Non-Developer Installation & Usage Guide

This guide is for users running **Ubuntu 24.04** with only Git and Docker installed. No JVM, Python, or any language runtime is required.

## Prerequisites

- Ubuntu 24.04
- A user account with sudo privileges

## 1. Install Docker

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
newgrp docker
```

Verify the installation:

```bash
docker --version
docker compose version
```

## 2. Install Git (if not already installed)

```bash
sudo apt-get update && sudo apt-get install -y git
```

## 3. Log in to GitHub Container Registry

A GitHub personal access token with `read:packages` scope is required to pull the CI/CD system images.

To create one, go to https://github.com/settings/tokens and generate a **classic** token with the `read:packages` permission.

```bash
echo "<YOUR_GITHUB_TOKEN>" | docker login ghcr.io -u <YOUR_GITHUB_USERNAME> --password-stdin
```

## 4. Download System Files

Create a working directory and download the two required files:

```bash
mkdir ~/cicd-system && cd ~/cicd-system

curl -H "Authorization: token <YOUR_GITHUB_TOKEN>" \
  -o docker-compose.yaml \
  https://raw.githubusercontent.com/CS7580-SEA-SP26/d-team/main/docker-compose.yaml

curl -H "Authorization: token <YOUR_GITHUB_TOKEN>" \
  -o cicd \
  https://raw.githubusercontent.com/CS7580-SEA-SP26/d-team/main/cicd

chmod +x cicd
```

## 5. Install the CLI to System PATH

This allows you to run `cicd` from any directory:

```bash
sudo cp ~/cicd-system/cicd /usr/local/bin/cicd
```

## 6. Start the System

```bash
cd ~/cicd-system
docker compose up -d
```

Wait about 30 seconds for all services to initialize, then verify everything is running:

```bash
docker compose ps
```

You should see four services running: `cicd-postgres`, `cicd-rabbitmq`, `cicd-server`, and `cicd-worker`.

If any service is not running, check its logs:

```bash
docker compose logs server
docker compose logs worker
```

## 7. Usage Examples

### 7.1 Clone the Example Repositories

```bash
# Repository with a passing pipeline
git clone https://github.com/lietcooper/sucess-demo-repo.git ~/success-demo

# Repository with a failing pipeline
git clone https://github.com/lietcooper/fail-demo-repo.git ~/fail-demo
```

### 7.2 Verify a Pipeline Configuration

The `verify` command checks whether a pipeline YAML file is valid.

```bash
# Verify a single file
cd ~/success-demo
cicd verify .pipelines/default.yaml

# Verify all YAML files in a directory
cicd verify .pipelines/
```

### 7.3 Dry Run a Pipeline

The `dryrun` command validates the configuration and prints the execution order without actually running anything.

```bash
cd ~/success-demo
cicd dryrun .pipelines/default.yaml
```

### 7.4 Run a Pipeline

The `run` command submits a pipeline for execution on the server. The server clones the repository, parses the configuration, and executes all jobs in Docker containers.

```bash
# Run a successful pipeline
cicd run --repo-url https://github.com/lietcooper/sucess-demo-repo.git --name default

# Run a failing pipeline
cicd run --repo-url https://github.com/lietcooper/fail-demo-repo.git --name default
```

After submitting, you will see a confirmation with the pipeline name and run number.

### 7.5 View Reports

The `report` command queries past pipeline execution results at four levels of detail.

```bash
# Level 1: All runs for a pipeline
cicd report --pipeline default

# Level 2: Details of a specific run
cicd report --pipeline default --run 1

# Level 3: Details of a specific stage in a run
cicd report --pipeline default --run 1 --stage build

# Level 4: Details of a specific job in a stage
cicd report --pipeline default --run 1 --stage build --job compile
```

### 7.6 View CLI Help

```bash
cicd --help
cicd verify --help
cicd dryrun --help
cicd run --help
cicd report --help
```

## 8. Stopping the System

```bash
cd ~/cicd-system
docker compose down -v
```

This stops all containers and removes the data volumes. To stop without deleting data, omit `-v`:

```bash
docker compose down
```

## 9. Troubleshooting

| Problem | Solution |
|---------|----------|
| `docker: permission denied` | Run `sudo usermod -aG docker $USER && newgrp docker` |
| `cicd: command not found` | Run `sudo cp ~/cicd-system/cicd /usr/local/bin/cicd` |
| Server/worker not starting | Check logs: `docker compose logs server` |
| Cannot pull images | Verify GHCR login: `docker login ghcr.io` |
| Port conflict on 8080/5432 | Stop conflicting services or edit ports in `docker-compose.yaml` |

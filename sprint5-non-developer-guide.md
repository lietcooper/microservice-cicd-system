# Non-Developer Installation & Usage Guide

This guide is for evaluators using **Ubuntu 24.04**. The only software assumed to be pre-installed is **Git**. Docker will be installed during setup. No local JVM, Python, or any other language runtime is required because both the CLI and the system components run through Docker images.

## Prerequisites

- Ubuntu 24.04
- A user account with sudo privileges
- Git

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

## 2. Create a GitHub Access Token

Because the repository is private, create a GitHub personal access token before downloading any evaluator files. A classic token with `repo` access is sufficient for downloading files from the private repository. If the container images are also private, the same token can be used for Docker login.

Create the token at:
- `https://github.com/settings/tokens`

## 3. Download Evaluator Files

Create a working directory and download the evaluator compose file and CLI launcher script. The `cicd` file is a small Docker-based launcher that runs the published CLI image:

```bash
mkdir ~/cicd-system && cd ~/cicd-system

curl -H "Authorization: token <YOUR_GITHUB_TOKEN>" -O https://raw.githubusercontent.com/CS7580-SEA-SP26/d-team/main/docker-compose.evaluator.yaml
curl -H "Authorization: token <YOUR_GITHUB_TOKEN>" -O https://raw.githubusercontent.com/CS7580-SEA-SP26/d-team/main/cicd
chmod +x cicd
```

## 4. Log In to GitHub Container Registry

If the published images are private, log in before starting the system. This applies to the `server`, `worker`, and `cli` images. If the images are public, skip this step.

```bash
echo "<YOUR_GITHUB_TOKEN>" | docker login ghcr.io -u <YOUR_GITHUB_USERNAME> --password-stdin
```

## 5. Install the CLI to System PATH

This allows you to run `cicd` from any directory:

```bash
sudo cp ~/cicd-system/cicd /usr/local/bin/cicd
```

Verify the CLI launcher:

```bash
cicd --help
```

You can also run the launcher directly without installing it to PATH:

```bash
~/cicd-system/cicd --help
```

The launcher runs the CLI container with host networking so it can reach the local REST service on `localhost:8080`.

## 6. Start the System Components

```bash
cd ~/cicd-system
docker compose -f docker-compose.evaluator.yaml up -d
```

Wait about 30 seconds for all services to initialize.

## 7. Verify System Health

Check container status:

```bash
docker compose -f docker-compose.evaluator.yaml ps
```

You should see these four services running:
- `cicd-postgres`
- `cicd-rabbitmq`
- `cicd-server`
- `cicd-worker`

Check server logs:

```bash
docker compose -f docker-compose.evaluator.yaml logs server
```

Check worker logs:

```bash
docker compose -f docker-compose.evaluator.yaml logs worker
```

Check RabbitMQ management UI if needed:
- URL: `http://localhost:15672`
- Username: `cicd`
- Password: `cicd`

## 8. Clone the Example Repositories

```bash
git clone https://github.com/lietcooper/success-demo-repo.git ~/success-demo
git clone https://github.com/lietcooper/fail-demo-repo.git ~/fail-demo
```

## 9. Local Validation Examples

```bash
cd ~/success-demo
cicd verify .pipelines/default.yaml
cicd dryrun .pipelines/default.yaml
cicd verify .pipelines/invalid-cycle.yaml
```

## 10. Successful Pipeline Run

```bash
cicd run --repo-url https://github.com/lietcooper/success-demo-repo.git --name default --branch main
cicd report --pipeline default
cicd report --pipeline default --run 1
cicd report --pipeline default --run 1 --stage build
cicd report --pipeline default --run 1 --stage build --job compile
```

## 11. Failed Pipeline Run

```bash
cicd run --repo-url https://github.com/lietcooper/fail-demo-repo.git --name default --branch main
cicd report --pipeline default --run 2
cicd report --pipeline default --run 2 --stage test
cicd report --pipeline default --run 2 --stage test --job tests
```

## 12. Stop the System

```bash
cd ~/cicd-system
docker compose -f docker-compose.evaluator.yaml down -v
```

To stop without deleting data:

```bash
docker compose -f docker-compose.evaluator.yaml down
```

## 13. Troubleshooting

| Problem | Solution |
|---------|----------|
| `docker: permission denied` | Run `sudo usermod -aG docker $USER && newgrp docker` |
| `cicd: command not found` | Run `sudo cp ~/cicd-system/cicd /usr/local/bin/cicd` |
| CLI cannot connect to server | Verify `cicd-server` is running with `docker compose -f docker-compose.evaluator.yaml ps` |
| Server or worker not starting | Check logs with `docker compose -f docker-compose.evaluator.yaml logs server` or `worker` |
| Cannot pull images | If images are private, run `docker login ghcr.io` before using `cicd` or starting compose |
| `cicd --help` fails immediately | Check Docker access, then verify the CLI image can be pulled from GHCR |
| Port conflict on 8080/5432/15672 | Stop conflicting services or edit `docker-compose.evaluator.yaml` |

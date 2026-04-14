#!/bin/bash
# ================================================================
# Observability + Artifacts Demo — Minikube
# ================================================================
# USAGE:
#   1. ./demo-observability.sh setup   — port-forward + pre-seed data
#   2. ./demo-observability.sh         — interactive demo (Enter to advance)
# ================================================================

cd /Users/yski/Desktop/Aneu_class/CS7580/d-team

cicd() {
  java -jar cli/build/libs/cli-0.1.0.jar "$@"
}

SERVER="http://localhost:8080"
REPO="https://github.com/lietcooper/fail-demo-repo"
PASS_FILE=".pipelines/demo-success.yaml"
FAIL_FILE=".pipelines/demo-fail.yaml"
PASS_PIPELINE="demo-success"
FAIL_PIPELINE="demo-fail"

run() {
  local display="$1"
  local cmd="$2"
  echo ""
  echo -e "\033[1;32m\$\033[0m $display"
  read -s -p ""
  eval "$cmd"
}

run_capture() {
  local display="$1"
  local cmd="$2"
  echo ""
  echo -e "\033[1;32m\$\033[0m $display"
  read -s -p ""
  LAST_OUTPUT=$(eval "$cmd" 2>&1)
  echo "$LAST_OUTPUT"
}

header() {
  read -s -p ""
  echo ""
  echo "========================================"
  echo "  $1"
  echo "========================================"
}

# ================================================================
# SETUP MODE
# ================================================================
if [ "${1:-}" = "setup" ]; then
  echo "=== Checking Minikube ==="
  if ! minikube status | grep -q "Running"; then
    echo "ERROR: Minikube is not running. Start it with: minikube start --cpus=4 --memory=7168"
    exit 1
  fi

  echo "=== Setting up port-forwards ==="
  pkill -f "port-forward.*cicd" 2>/dev/null
  sleep 1
  kubectl port-forward svc/cicd-cicd-server     8080:8080 -n cicd &>/dev/null &
  kubectl port-forward svc/cicd-cicd-grafana     3000:3000 -n cicd &>/dev/null &
  kubectl port-forward svc/cicd-cicd-prometheus  9090:9090 -n cicd &>/dev/null &
  echo "Waiting for port-forwards..."
  sleep 4

  if ! curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
    echo "ERROR: Server not reachable at localhost:8080"
    exit 1
  fi
  echo "Server is UP."

  echo ""
  echo "=== Pre-seeding pipeline runs for Prometheus graphs ==="
  for i in 1 2 3; do
    echo "  Seed round $i/3..."
    cicd run --file $PASS_FILE --repo-url $REPO --server $SERVER >/dev/null 2>&1
    cicd run --file $FAIL_FILE --repo-url $REPO --server $SERVER >/dev/null 2>&1
    sleep 12
  done

  echo ""
  echo "============================================"
  echo "  Setup complete!"
  echo ""
  echo "  Port-forwards active:"
  echo "    Server:     http://localhost:8080"
  echo "    Grafana:    http://localhost:3000  (admin/admin)"
  echo "    Prometheus: http://localhost:9090"
  echo ""
  echo "  Run the demo:  ./demo-observability.sh"
  echo "============================================"
  exit 0
fi

# ================================================================
# INTERACTIVE DEMO
# ================================================================

clear

# ---------------------------------------------------------------
header "Part 0: Environment Overview"
# ---------------------------------------------------------------

run \
"kubectl get pods -n cicd" \
"kubectl get pods -n cicd"

run \
"tree helm/cicd/ -L 2" \
"tree helm/cicd/ -L 2"

# ---------------------------------------------------------------
header "Part 1: Trigger Pipelines"
# ---------------------------------------------------------------

run \
"cat .pipelines/demo-success.yaml" \
"cat .pipelines/demo-success.yaml"

run_capture \
"cicd run \\
  --file $PASS_FILE \\
  --repo-url $REPO \\
  --server $SERVER" \
"cicd run --file $PASS_FILE --repo-url $REPO --server $SERVER"
PASS_RUN=$(echo "$LAST_OUTPUT" | grep -o 'Run #: [0-9]*' | grep -o '[0-9]*')

run \
"sleep 10 && cicd status \\
  --file $PASS_FILE \\
  --run $PASS_RUN \\
  --server $SERVER" \
"sleep 10 && cicd status --file $PASS_FILE --run $PASS_RUN --server $SERVER"

run_capture \
"cicd run \\
  --file $FAIL_FILE \\
  --repo-url $REPO \\
  --server $SERVER" \
"cicd run --file $FAIL_FILE --repo-url $REPO --server $SERVER"
FAIL_RUN=$(echo "$LAST_OUTPUT" | grep -o 'Run #: [0-9]*' | grep -o '[0-9]*')

run \
"sleep 10 && cicd status \\
  --file $FAIL_FILE \\
  --run $FAIL_RUN \\
  --server $SERVER" \
"sleep 10 && cicd status --file $FAIL_FILE --run $FAIL_RUN --server $SERVER"

# ---------------------------------------------------------------
header "Part 2: Artifacts in Report"
# ---------------------------------------------------------------

run \
"cicd report --pipeline $PASS_PIPELINE --run $PASS_RUN --stage build --server $SERVER" \
"cicd report --pipeline $PASS_PIPELINE --run $PASS_RUN --stage build --server $SERVER"

run \
"cicd report --pipeline $PASS_PIPELINE --run $PASS_RUN --stage test --server $SERVER" \
"cicd report --pipeline $PASS_PIPELINE --run $PASS_RUN --stage test --server $SERVER"

# ---------------------------------------------------------------
header "Part 3: Report with trace-id"
# ---------------------------------------------------------------

run \
"cicd report --pipeline $PASS_PIPELINE --server $SERVER" \
"cicd report --pipeline $PASS_PIPELINE --server $SERVER"

run \
"cicd report --pipeline $PASS_PIPELINE --run $PASS_RUN --server $SERVER" \
"cicd report --pipeline $PASS_PIPELINE --run $PASS_RUN --server $SERVER"

# ---------------------------------------------------------------
header "Part 4: Metrics Endpoint"
# ---------------------------------------------------------------

run \
"curl -s localhost:8080/actuator/prometheus | grep cicd_ | grep -v bucket" \
"curl -s localhost:8080/actuator/prometheus | grep cicd_ | grep -v bucket"

# ---------------------------------------------------------------
header "Part 5: Dashboard 1 — Pipeline Overview"
# ---------------------------------------------------------------

echo ""
echo "  >>> Switch to browser: http://localhost:3000/d/cicd-pipeline-overview"
read -s -p ""

# ---------------------------------------------------------------
header "Part 6: Dashboard 4 — Trace Explorer"
# ---------------------------------------------------------------

echo ""
echo "  >>> Switch to browser: http://localhost:3000/d/cicd-trace-explorer"
read -s -p ""

# ---------------------------------------------------------------
header "Part 7: Dashboard 2 — Stage & Job Breakdown"
# ---------------------------------------------------------------

echo ""
echo "  >>> Switch to browser: http://localhost:3000/d/cicd-stage-job-breakdown"
read -s -p ""

# ---------------------------------------------------------------
header "Part 8: Dashboard 3 — Logs Viewer"
# ---------------------------------------------------------------

echo ""
echo "  >>> Switch to browser: http://localhost:3000/d/cicd-logs-viewer"
read -s -p ""

# ---------------------------------------------------------------
header "Part 9: Configuration as Code"
# ---------------------------------------------------------------

run \
"ls helm/cicd/dashboards/" \
"ls helm/cicd/dashboards/"

run \
"head -20 helm/cicd/templates/grafana-configmap.yaml" \
"head -20 helm/cicd/templates/grafana-configmap.yaml"

run \
"head -30 helm/cicd/templates/grafana-dashboards-configmap.yaml" \
"head -30 helm/cicd/templates/grafana-dashboards-configmap.yaml"

# ---------------------------------------------------------------
header "Part 10: Prometheus Alert Rules"
# ---------------------------------------------------------------

run \
"curl -s localhost:9090/api/v1/rules | python3 -m json.tool | head -60" \
"curl -s localhost:9090/api/v1/rules | python3 -m json.tool | head -60"

# ---------------------------------------------------------------
echo ""
echo "========================================"
echo "  Demo Complete"
echo "========================================"

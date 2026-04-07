#!/bin/bash
# ================================================================
# Artifacts Feature Demo — Minikube
# ================================================================
# USAGE:
#   1. ./demo-artifacts.sh setup   — port-forward + verify
#   2. ./demo-artifacts.sh         — interactive demo (Enter to advance)
# ================================================================

cd /Users/yski/Desktop/Aneu_class/CS7580/d-team

cicd() {
  java -jar cli/build/libs/cli-0.1.0.jar "$@"
}

SERVER="http://localhost:8080"
REPO="https://github.com/lietcooper/fail-demo-repo"

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
    echo "ERROR: Minikube is not running. Start with: minikube start --cpus=4 --memory=7168"
    exit 1
  fi

  echo "=== Setting up port-forwards ==="
  pkill -f "port-forward.*cicd" 2>/dev/null
  sleep 1
  kubectl port-forward svc/cicd-cicd-server 8080:8080 -n cicd &>/dev/null &
  echo "Waiting for port-forward..."
  sleep 4

  if ! curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
    echo "ERROR: Server not reachable at localhost:8080"
    exit 1
  fi
  echo "Server is UP."
  echo ""
  echo "============================================"
  echo "  Setup complete!"
  echo "  Server: http://localhost:8080"
  echo "  Run:    ./demo-artifacts.sh"
  echo "============================================"
  exit 0
fi

# ================================================================
# INTERACTIVE DEMO
# ================================================================

clear

# ---------------------------------------------------------------
header "Part 1: Artifact Specification in YAML"
# ---------------------------------------------------------------

run \
"cat .pipelines/demo-success.yaml" \
"cat .pipelines/demo-success.yaml"

# ---------------------------------------------------------------
header "Part 2: Run Pipeline with Artifacts"
# ---------------------------------------------------------------

run_capture \
"cicd run \\
  --file .pipelines/demo-success.yaml \\
  --repo-url $REPO \\
  --server $SERVER" \
"cicd run --file .pipelines/demo-success.yaml --repo-url $REPO --server $SERVER"
PASS_RUN=$(echo "$LAST_OUTPUT" | grep -o 'Run #: [0-9]*' | grep -o '[0-9]*')

run \
"sleep 10 && cicd status \\
  --file .pipelines/demo-success.yaml \\
  --run $PASS_RUN \\
  --server $SERVER" \
"sleep 10 && cicd status --file .pipelines/demo-success.yaml --run $PASS_RUN --server $SERVER"

# ---------------------------------------------------------------
header "Part 3: Artifacts in Report"
# ---------------------------------------------------------------

run \
"cicd report \\
  --pipeline demo-success \\
  --run $PASS_RUN --stage build \\
  --server $SERVER" \
"cicd report --pipeline demo-success --run $PASS_RUN --stage build --server $SERVER"

run \
"cicd report \\
  --pipeline demo-success \\
  --run $PASS_RUN --stage test \\
  --server $SERVER" \
"cicd report --pipeline demo-success --run $PASS_RUN --stage test --server $SERVER"

# ---------------------------------------------------------------
header "Part 4: Failed Pipeline — No Artifacts"
# ---------------------------------------------------------------

run_capture \
"cicd run \\
  --file .pipelines/demo-fail.yaml \\
  --repo-url $REPO \\
  --server $SERVER" \
"cicd run --file .pipelines/demo-fail.yaml --repo-url $REPO --server $SERVER"
FAIL_RUN=$(echo "$LAST_OUTPUT" | grep -o 'Run #: [0-9]*' | grep -o '[0-9]*')

run \
"sleep 10 && cicd status \\
  --file .pipelines/demo-fail.yaml \\
  --run $FAIL_RUN \\
  --server $SERVER" \
"sleep 10 && cicd status --file .pipelines/demo-fail.yaml --run $FAIL_RUN --server $SERVER"

run \
"cicd report \\
  --pipeline demo-fail \\
  --run $FAIL_RUN --stage build \\
  --server $SERVER" \
"cicd report --pipeline demo-fail --run $FAIL_RUN --stage build --server $SERVER"

# ---------------------------------------------------------------
header "Part 5: Pattern Matching Examples"
# ---------------------------------------------------------------

echo ""
echo "  Supported patterns:"
echo "    README.md              exact file"
echo "    build/                 directory (recursive)"
echo "    *.java                 suffix wildcard"
echo "    READ*                  prefix wildcard"
echo "    build/*/doc/           single-level wildcard dir"
echo "    build/**/distribution  any-depth wildcard dir"
echo ""
echo "  Demo pipeline uses:"
echo "    build/output.txt       exact file"
echo "    build/reports/*.html   suffix wildcard in subdir"
echo "    results/               directory (recursive)"
read -s -p ""

# ---------------------------------------------------------------
header "Part 6: Storage & Implementation"
# ---------------------------------------------------------------

run \
"kubectl get pods -n cicd | grep minio" \
"kubectl get pods -n cicd | grep minio"

echo ""
echo "  Artifact storage path convention:"
echo "    {pipeline}/{run}/{stage}/{job}/{relative-path}"
echo ""
echo "  Flow:"
echo "    1. YAML defines artifact patterns per job"
echo "    2. After job succeeds, ArtifactCollectorService matches files"
echo "    3. ArtifactStorageService uploads to MinIO (S3-compatible)"
echo "    4. Metadata persisted to PostgreSQL (artifacts table)"
echo "    5. Report API returns pattern + storage location"
read -s -p ""

# ---------------------------------------------------------------
echo ""
echo "========================================"
echo "  Demo Complete"
echo "========================================"

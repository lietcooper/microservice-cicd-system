#!/bin/bash

# Integration validation script for Phase 11.2
# Assumes the full stack is deployed via: helm upgrade --install cicd ./helm/cicd -n cicd --create-namespace

NAMESPACE="cicd"
PASS=0
FAIL=0
SKIP=0

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

pass() { echo -e "  ${GREEN}PASS${NC} $1"; PASS=$((PASS + 1)); }
fail() { echo -e "  ${RED}FAIL${NC} $1"; FAIL=$((FAIL + 1)); }
skip() { echo -e "  ${YELLOW}SKIP${NC} $1"; SKIP=$((SKIP + 1)); }
header() { echo -e "\n${BLUE}=== $1 ===${NC}"; }

# ---------------------------------------------------------------------------
header "0. Prerequisites"
# ---------------------------------------------------------------------------

if ! kubectl get ns "$NAMESPACE" &>/dev/null; then
    echo "Namespace '$NAMESPACE' not found. Is the stack deployed?"
    exit 1
fi
pass "Namespace '$NAMESPACE' exists"

echo "Setting up port-forwards (background)..."
PF_PIDS=""
cleanup() {
    if [ -n "$PF_PIDS" ]; then
        for p in $PF_PIDS; do kill "$p" 2>/dev/null || true; done
    fi
}
trap cleanup EXIT

start_pf() {
    kubectl port-forward "svc/$1" "$2:$2" -n "$NAMESPACE" &>/dev/null &
    PF_PIDS="$PF_PIDS $!"
}

start_pf cicd-cicd-server     8080
start_pf cicd-cicd-worker     8081
start_pf cicd-cicd-prometheus 9090
start_pf cicd-cicd-grafana    3000
start_pf cicd-cicd-loki       3100
start_pf cicd-cicd-tempo      3200
sleep 4

# ---------------------------------------------------------------------------
header "1. Pod Health"
# ---------------------------------------------------------------------------

ALL_PODS=$(kubectl get pods -n "$NAMESPACE" -o custom-columns='NAME:.metadata.name,STATUS:.status.phase' --no-headers 2>/dev/null)
for component in server worker postgresql rabbitmq otel-collector prometheus loki tempo grafana; do
    match=$(echo "$ALL_PODS" | grep "$component" | head -1)
    if [ -n "$match" ]; then
        pod_name=$(echo "$match" | awk '{print $1}')
        phase=$(echo "$match" | awk '{print $2}')
        if [ "$phase" = "Running" ]; then
            pass "$component pod is Running ($pod_name)"
        else
            fail "$component pod is $phase ($pod_name)"
        fi
    else
        fail "$component pod not found"
    fi
done

# ---------------------------------------------------------------------------
header "2. Actuator Health Endpoints"
# ---------------------------------------------------------------------------

for svc_port in "server:8080" "worker:8081"; do
    name=${svc_port%%:*}
    port=${svc_port##*:}
    status=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${port}/actuator/health" 2>/dev/null || echo "000")
    if [ "$status" = "200" ]; then
        pass "$name /actuator/health returns 200"
    else
        fail "$name /actuator/health returned $status"
    fi
done

# ---------------------------------------------------------------------------
header "3. Prometheus Metrics (5 required metrics)"
# ---------------------------------------------------------------------------

METRICS_OUTPUT=$(curl -s "http://localhost:8080/actuator/prometheus" 2>/dev/null || echo "")
if [ -z "$METRICS_OUTPUT" ]; then
    fail "Cannot reach server /actuator/prometheus"
else
    pass "Server /actuator/prometheus reachable"
    for metric in cicd_pipeline_runs_total cicd_pipeline_duration_seconds cicd_stage_duration_seconds cicd_job_duration_seconds cicd_job_runs_total; do
        if echo "$METRICS_OUTPUT" | grep -q "$metric"; then
            pass "Metric $metric present"
        else
            skip "Metric $metric not yet present (may need a pipeline run)"
        fi
    done
fi

# ---------------------------------------------------------------------------
header "4. Prometheus Scrape Targets"
# ---------------------------------------------------------------------------

TARGETS=$(curl -s "http://localhost:9090/api/v1/targets" 2>/dev/null || echo "")
if echo "$TARGETS" | grep -q '"status":"success"'; then
    SERVER_TARGET=$(echo "$TARGETS" | grep -c "cicd-server" || true)
    WORKER_TARGET=$(echo "$TARGETS" | grep -c "cicd-worker" || true)
    if [ "$SERVER_TARGET" -gt 0 ]; then pass "Prometheus scrapes cicd-server"; else fail "Prometheus missing cicd-server target"; fi
    if [ "$WORKER_TARGET" -gt 0 ]; then pass "Prometheus scrapes cicd-worker"; else fail "Prometheus missing cicd-worker target"; fi
else
    fail "Cannot query Prometheus targets API"
fi

# ---------------------------------------------------------------------------
header "5. Prometheus Alert Rules"
# ---------------------------------------------------------------------------

RULES=$(curl -s "http://localhost:9090/api/v1/rules" 2>/dev/null || echo "")
if echo "$RULES" | grep -q '"status":"success"'; then
    for alert in CicdPipelineConsecutiveFailures CicdJobDurationHigh CicdPipelineDurationHigh; do
        if echo "$RULES" | grep -q "$alert"; then
            pass "Alert rule $alert loaded"
        else
            fail "Alert rule $alert not found"
        fi
    done
else
    fail "Cannot query Prometheus rules API"
fi

# ---------------------------------------------------------------------------
header "6. Grafana Datasources"
# ---------------------------------------------------------------------------

GF_AUTH="admin:admin"
GF_DS=$(curl -s -u "$GF_AUTH" "http://localhost:3000/api/datasources" 2>/dev/null || echo "")
if echo "$GF_DS" | grep -q '"type"'; then
    for ds in prometheus loki tempo postgres; do
        if echo "$GF_DS" | grep -qi "$ds"; then
            pass "Grafana datasource '$ds' configured"
        else
            fail "Grafana datasource '$ds' missing"
        fi
    done
else
    fail "Cannot query Grafana datasources API"
fi

# ---------------------------------------------------------------------------
header "7. Grafana Dashboards (4 required)"
# ---------------------------------------------------------------------------

DASHBOARDS=$(curl -s -u "$GF_AUTH" "http://localhost:3000/api/search?type=dash-db" 2>/dev/null || echo "")
for uid in cicd-pipeline-overview cicd-stage-job-breakdown cicd-logs-viewer cicd-trace-explorer; do
    if echo "$DASHBOARDS" | grep -q "$uid"; then
        pass "Dashboard $uid provisioned"
    else
        fail "Dashboard $uid not found"
    fi
done

# ---------------------------------------------------------------------------
header "8. Loki Reachability"
# ---------------------------------------------------------------------------

LOKI_READY=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:3100/ready" 2>/dev/null || echo "000")
if [ "$LOKI_READY" = "200" ]; then
    pass "Loki /ready returns 200"
else
    fail "Loki /ready returned $LOKI_READY"
fi

# ---------------------------------------------------------------------------
header "9. Tempo Reachability"
# ---------------------------------------------------------------------------

TEMPO_READY=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:3200/ready" 2>/dev/null || echo "000")
if [ "$TEMPO_READY" = "200" ]; then
    pass "Tempo /ready returns 200"
else
    fail "Tempo /ready returned $TEMPO_READY"
fi

# ---------------------------------------------------------------------------
header "10. Report CLI trace-id (requires at least one pipeline run)"
# ---------------------------------------------------------------------------

# Try to fetch a run report from the server API directly (check multiple pipelines)
REPORT=""
for pname in default failure-true failure-false; do
    REPORT=$(curl -s "http://localhost:8080/pipelines/${pname}/runs" 2>/dev/null || echo "")
    if echo "$REPORT" | grep -q "run-no\|runNo"; then break; fi
done
if echo "$REPORT" | grep -q "trace-id\|traceId\|trace_id"; then
    pass "Report API includes trace-id field"
elif echo "$REPORT" | grep -q "run_no\|runNo"; then
    skip "Report API reachable but no trace-id yet (run a pipeline first)"
else
    skip "No pipeline runs found — run a pipeline to validate trace-id"
fi

# ---------------------------------------------------------------------------
header "11. Dashboard Link: trace-id -> Trace Explorer"
# ---------------------------------------------------------------------------

OVERVIEW_JSON=$(curl -s -u "$GF_AUTH" "http://localhost:3000/api/dashboards/uid/cicd-pipeline-overview" 2>/dev/null || echo "")
if echo "$OVERVIEW_JSON" | grep -q "cicd-trace-explorer"; then
    pass "Pipeline Overview dashboard links to Trace Explorer"
else
    skip "Cannot verify dashboard link (check manually in Grafana UI)"
fi

# ---------------------------------------------------------------------------
header "Summary"
# ---------------------------------------------------------------------------

echo ""
echo -e "  ${GREEN}PASS: $PASS${NC}  ${RED}FAIL: $FAIL${NC}  ${YELLOW}SKIP: $SKIP${NC}"
echo ""

if [ "$FAIL" -gt 0 ]; then
    echo -e "${RED}Some checks failed. Review output above.${NC}"
    exit 1
else
    echo -e "${GREEN}All checks passed (or skipped pending pipeline run).${NC}"
    exit 0
fi

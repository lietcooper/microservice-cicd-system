# TODO.md — Observability Feature Implementation Checklist

> Execute tasks in order. Each task is designed to be independently compilable and testable.
> After completing each section, run `./gradlew clean build -x test` to verify compilation.

---

## Phase 1: Dependencies & Foundation

### 1.1 Add OpenTelemetry + Micrometer dependencies
- [x] Add OTel BOM and dependencies to `server/build.gradle`:
  - `io.opentelemetry:opentelemetry-bom:1.40.0` (platform)
  - `io.opentelemetry:opentelemetry-api`
  - `io.opentelemetry:opentelemetry-sdk`
  - `io.opentelemetry:opentelemetry-exporter-otlp`
  - `io.opentelemetry:opentelemetry-sdk-extension-autoconfigure`
  - `io.micrometer:micrometer-registry-prometheus`
  - `org.springframework.boot:spring-boot-starter-actuator`
  - `net.logstash.logback:logstash-logback-encoder:7.4`
  - `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.6.0-alpha` (for trace-id in logs)
  - **Files**: `server/build.gradle`

- [x] Add same OTel + logging dependencies to `worker/build.gradle`:
  - Same OTel BOM + api + sdk + exporter-otlp + autoconfigure
  - `net.logstash.logback:logstash-logback-encoder:7.4`
  - `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.6.0-alpha`
  - **Files**: `worker/build.gradle`

- [x] Add SLF4J API to `common/build.gradle` (for structured logging in shared code):
  - `org.slf4j:slf4j-api:2.0.12`
  - **Files**: `common/build.gradle`

- [x] Verify compilation: `./gradlew clean build -x test`

### 1.2 Database migration for trace_id
- [x] Create `server/src/main/resources/db/migration/V3__add_trace_id_to_pipeline_runs.sql`:
  ```sql
  ALTER TABLE pipeline_runs ADD COLUMN trace_id VARCHAR(32);
  ```
  - **Files**: `server/src/main/resources/db/migration/V3__add_trace_id_to_pipeline_runs.sql`

- [x] Add `traceId` field to `PipelineRunEntity.java`:
  ```java
  @Column(name = "trace_id", length = 32)
  private String traceId;
  ```
  - **Files**: `server/src/main/java/cicd/persistence/entity/PipelineRunEntity.java`

---

## Phase 2: Structured Logging

### 2.1 Configure Logback for structured JSON output
- [x] Create `server/src/main/resources/logback-spring.xml` with:
  - Console appender using `LogstashEncoder` (structured JSON)
  - Include `traceId` and `spanId` fields via OTel Logback appender
  - Log level configurable via Spring profile
  - **Files**: `server/src/main/resources/logback-spring.xml`

- [x] Create `worker/src/main/resources/logback-spring.xml` with same structure
  - **Files**: `worker/src/main/resources/logback-spring.xml`

### 2.2 Convert System.out to SLF4J in modified files
- [x] Convert `PipelineOrchestrationListener.java` — replace all `System.out.println` / `System.err.println` with `private static final Logger log = LoggerFactory.getLogger(...)` and structured log calls. Use MDC for pipeline/run_no/stage/job context.
  - **Files**: `worker/src/main/java/cicd/listener/PipelineOrchestrationListener.java`

- [x] Convert `JobExecutionListener.java` — same pattern
  - **Files**: `worker/src/main/java/cicd/listener/JobExecutionListener.java`

- [x] Convert `JobResultListener.java` — same pattern
  - **Files**: `worker/src/main/java/cicd/listener/JobResultListener.java`

- [x] Convert `StatusUpdateListener.java` — same pattern
  - **Files**: `server/src/main/java/cicd/listener/StatusUpdateListener.java`

- [x] Convert `PipelineMessagePublisher.java` — same pattern
  - **Files**: `server/src/main/java/cicd/messaging/PipelineMessagePublisher.java`

- [x] Convert `StatusUpdatePublisher.java` and `StatusEventPublisher.java` — same pattern
  - **Files**: `worker/src/main/java/cicd/service/StatusUpdatePublisher.java`, `worker/src/main/java/cicd/service/StatusEventPublisher.java`

---

## Phase 3: OpenTelemetry SDK Configuration

### 3.1 Create OTel configuration beans
- [x] Create `server/src/main/java/cicd/config/OpenTelemetryConfig.java`:
  - Configure `SdkTracerProvider` with OTLP exporter
  - Configure `SdkMeterProvider` (for exemplars)
  - Set service name to `cicd-server`
  - OTLP endpoint configurable via `OTEL_EXPORTER_OTLP_ENDPOINT` env var (default `http://localhost:4317`)
  - Export a `Tracer` bean
  - **Files**: `server/src/main/java/cicd/config/OpenTelemetryConfig.java`

- [x] Create `worker/src/main/java/cicd/config/OpenTelemetryConfig.java`:
  - Same structure, service name `cicd-worker`
  - **Files**: `worker/src/main/java/cicd/config/OpenTelemetryConfig.java`

### 3.2 Configure Spring Actuator + Prometheus metrics endpoint
- [x] Add to `server/src/main/resources/application.properties`:
  ```properties
  management.endpoints.web.exposure.include=health,prometheus,info
  management.metrics.export.prometheus.enabled=true
  management.endpoint.health.show-details=always
  ```
  - **Files**: `server/src/main/resources/application.properties`

- [x] Add to `worker/src/main/resources/application.properties`:
  ```properties
  management.endpoints.web.exposure.include=health,prometheus,info
  management.metrics.export.prometheus.enabled=true
  management.endpoint.health.show-details=always
  ```
  - **Files**: `worker/src/main/resources/application.properties`

- [x] Verify: start server, `curl localhost:8080/actuator/prometheus` should return Prometheus format

---

## Phase 4: Metrics Instrumentation

### 4.1 Create metrics service
- [x] Create `server/src/main/java/cicd/observability/CicdMetrics.java`:
  - Inject `MeterRegistry`
  - Register the 5 required metrics using Micrometer API:
    - `cicd_pipeline_runs_total` — `Counter.builder("cicd.pipeline.runs.total").tag("pipeline", name).tag("status", status).register(registry)`
    - `cicd_pipeline_duration_seconds` — `Timer.builder("cicd.pipeline.duration.seconds").tag("pipeline", name).register(registry)`
    - `cicd_stage_duration_seconds` — `Timer.builder("cicd.stage.duration.seconds").tag("pipeline", name).tag("stage", stage).register(registry)`
    - `cicd_job_duration_seconds` — `Timer.builder("cicd.job.duration.seconds").tag("pipeline", name).tag("stage", stage).tag("job", job).register(registry)`
    - `cicd_job_runs_total` — `Counter.builder("cicd.job.runs.total").tag("pipeline", name).tag("stage", stage).tag("job", job).tag("status", status).register(registry)`
  - Provide methods: `recordPipelineCompleted(name, status, durationMs)`, `recordStageCompleted(...)`, `recordJobCompleted(...)`
  - Enable exemplars: attach trace-id to timer recordings
  - **Files**: `server/src/main/java/cicd/observability/CicdMetrics.java`

### 4.2 Instrument StatusUpdateListener to record metrics
- [x] Inject `CicdMetrics` into `StatusUpdateListener`
  - On pipeline completion (status change to SUCCESS/FAILED): call `recordPipelineCompleted`
  - On stage completion: call `recordStageCompleted`
  - On job completion: call `recordJobCompleted`
  - Calculate duration from `startTime` to `endTime` in the status update message
  - **Files**: `server/src/main/java/cicd/listener/StatusUpdateListener.java`

- [x] Verify: run a pipeline, check `curl localhost:8080/actuator/prometheus | grep cicd_`

---

## Phase 5: Distributed Tracing

### 5.1 Trace context propagation via RabbitMQ headers
- [ ] Create `common/src/main/java/cicd/observability/TraceContextHelper.java`:
  - `injectContext(MessageProperties props)` — uses `W3CTraceContextPropagator` to inject current span context into AMQP message headers
  - `extractContext(MessageProperties props)` — extracts context from AMQP headers, returns `Context`
  - **Files**: `common/src/main/java/cicd/observability/TraceContextHelper.java`, `common/build.gradle` (add otel-api + otel-context dependency)

- [ ] Create `server/src/main/java/cicd/config/TracingRabbitTemplate.java` (or modify `RabbitMqConfig`):
  - Wrap `RabbitTemplate` with a `MessagePostProcessor` that calls `TraceContextHelper.injectContext()` on every outgoing message
  - **Files**: `server/src/main/java/cicd/config/RabbitMqConfig.java`

- [ ] Do the same for worker's `RabbitTemplate`:
  - **Files**: `worker/src/main/java/cicd/config/RabbitMqConfig.java`

### 5.2 Add traceId field to messages
- [ ] Add `String traceId` field to `PipelineExecuteMessage`:
  - Server sets this when creating the root span, before publishing
  - Worker reads it and stores alongside the run
  - **Files**: `common/src/main/java/cicd/messaging/PipelineExecuteMessage.java`

- [ ] Add `String traceId` field to `StatusUpdateMessage`:
  - Worker sets this on pipeline-level status updates so server can persist trace_id
  - **Files**: `common/src/main/java/cicd/messaging/StatusUpdateMessage.java`

### 5.3 Instrument PipelineExecutionController (server-side root span creation)
- [ ] In `PipelineExecutionController` (or wherever the `PipelineRunEntity` is created and `PipelineExecuteMessage` is published):
  - Create root span: `tracer.spanBuilder("pipeline: " + name).startSpan()`
  - Extract trace-id: `span.getSpanContext().getTraceId()`
  - Set `traceId` on the `PipelineRunEntity` before saving to DB
  - Set `traceId` on the `PipelineExecuteMessage` before publishing
  - End span immediately (the real work happens in worker; server just initiates)
  - Actually — **alternative approach**: pass the trace context via message headers, let the Worker create the actual root span. Server just injects a "initiation span" that becomes parent.
  - Recommended: Server creates a short "pipeline.initiate" span → injects context into message → Worker extracts and creates long-running "pipeline.execute" root span. The trace-id from the server's initiation span IS the trace-id for the whole pipeline.
  - Store trace-id to DB here (before message publish).
  - **Files**: `server/src/main/java/cicd/api/controller/PipelineExecutionController.java`, `server/src/main/java/cicd/service/` (wherever PipelineRunEntity is created)

### 5.4 Instrument PipelineOrchestrationListener (worker-side pipeline span)
- [ ] In `onPipelineExecute()`:
  - Extract parent context from incoming message headers using `TraceContextHelper.extractContext()`
  - Create child span: `tracer.spanBuilder("pipeline: " + name).setParent(extractedCtx).startSpan()`
  - Set span attributes: `run_no`, `pipeline` (as required by spec)
  - Wrap entire method body in try-finally with `span.end()`
  - Make this span the current context using `span.makeCurrent()` (so child spans auto-attach)
  - **Files**: `worker/src/main/java/cicd/listener/PipelineOrchestrationListener.java`

- [ ] For each stage loop iteration:
  - Create child span: `tracer.spanBuilder("stage: " + stageName).startSpan()`
  - Set as current before dispatching jobs
  - End after stage completes
  - **Files**: `worker/src/main/java/cicd/listener/PipelineOrchestrationListener.java`

- [ ] When dispatching `JobExecuteMessage`:
  - Inject current span context (which is the stage span) into message headers
  - **Files**: `worker/src/main/java/cicd/listener/PipelineOrchestrationListener.java`

### 5.5 Instrument JobExecutionListener (job spans)
- [ ] In `onJobExecute()`:
  - Extract parent context from message headers (stage span)
  - Create child span: `tracer.spanBuilder("job: " + jobName).startSpan()`
  - Set attributes: pipeline, stage, job, image
  - Wrap Docker execution in try-finally with `span.end()`
  - On failure: `span.setStatus(StatusCode.ERROR)` and `span.recordException()`
  - **Files**: `worker/src/main/java/cicd/listener/JobExecutionListener.java`

### 5.6 Persist trace-id from StatusUpdateMessage
- [ ] In `StatusUpdateListener.handlePipeline()`:
  - If `msg.getTraceId() != null`, set `run.setTraceId(msg.getTraceId())`
  - **Files**: `server/src/main/java/cicd/listener/StatusUpdateListener.java`

- [ ] In `StatusUpdatePublisher.pipelineRunning()`:
  - Extract current trace-id from OTel context: `Span.current().getSpanContext().getTraceId()`
  - Set on the `StatusUpdateMessage`
  - **Files**: `worker/src/main/java/cicd/service/StatusUpdatePublisher.java`

- [ ] Verify: run a pipeline, check DB: `SELECT trace_id FROM pipeline_runs WHERE run_no = 1`

---

## Phase 6: Job Container Log Forwarding

### 6.1 Stream Docker container logs to OTel
- [ ] Modify `LocalDockerRunner.runJob()`:
  - After container starts, use `dockerClient.logContainerCmd(containerId).withFollowStream(true).withStdOut(true).withStdErr(true)` to stream logs in real-time
  - For each log frame, emit an OTel log record via `LoggerProvider` / SLF4J bridge with:
    - Attributes: `pipeline`, `run_no`, `stage`, `job`, `source=job-container`
    - The log message = the frame payload
  - Still capture full output into `StringBuilder` for `JobResult.output()` (existing behavior preserved)
  - **Files**: `worker/src/main/java/cicd/docker/LocalDockerRunner.java`

- [ ] Update `DockerRunner` interface to accept metadata context:
  - Add overload or parameter object: `JobContext { pipeline, runNo, stage, job }`
  - Or: set MDC before calling `runJob()` in `JobExecutionListener` (simpler, MDC flows into log appender)
  - Recommended: Use MDC approach — set MDC in `JobExecutionListener` before calling `dockerRunner.runJob()`, and in `LocalDockerRunner`, log each container output line via SLF4J (MDC automatically included)
  - **Files**: `worker/src/main/java/cicd/listener/JobExecutionListener.java`, `worker/src/main/java/cicd/docker/LocalDockerRunner.java`

- [ ] Add a "source" MDC field to distinguish system logs vs container logs:
  - System logs: `MDC.put("source", "system")`
  - Container logs: `MDC.put("source", "job-container")`
  - **Files**: various listener files

---

## Phase 7: Report Command Changes

### 7.1 Add trace-id to report API response
- [ ] Add `traceId` field to `PipelineRunDetailResponse.java`:
  ```java
  @JsonProperty("trace-id")
  private String traceId;
  ```
  - **Files**: `server/src/main/java/cicd/api/dto/PipelineRunDetailResponse.java`

- [ ] Add `traceId` to `RunSummaryDto.java` (for level-1 report):
  ```java
  @JsonProperty("trace-id")
  private String traceId;
  ```
  - **Files**: `server/src/main/java/cicd/api/dto/RunSummaryDto.java`

- [ ] Update `ReportService.java` to populate `traceId` from entity:
  - In `toRunSummary()`: `dto.setTraceId(entity.getTraceId())`
  - In `getRun()`, `getStage()`, `getJob()`: `response.setTraceId(run.getTraceId())`
  - **Files**: `server/src/main/java/cicd/service/ReportService.java`

### 7.2 Update CLI report output
- [ ] In `ReportCmd.printRunDetail()`:
  - Add line: `System.out.println("trace-id: " + text(json, "trace-id"));` after the status line
  - **Files**: `cli/src/main/java/cicd/cmd/ReportCmd.java`

- [ ] In `ReportCmd.printLevel1()` (per-run in the list):
  - Add: `System.out.println("    trace-id: " + text(rn, "trace-id"));`
  - **Files**: `cli/src/main/java/cicd/cmd/ReportCmd.java`

---

## Phase 8: Helm Chart — Observability Stack

### 8.1 Add OTel Collector
- [ ] Add to `helm/cicd/values.yaml`: `otelCollector` section (image: `otel/opentelemetry-collector-contrib`, ports: 4317 gRPC, 4318 HTTP)
- [ ] Create `helm/cicd/templates/otel-collector-deployment.yaml`: Deployment + Service
- [ ] Create `helm/cicd/templates/otel-collector-configmap.yaml`: Collector config YAML:
  - Receivers: `otlp` (grpc:4317, http:4318)
  - Exporters: `prometheus` (for metrics→Prometheus), `loki` (for logs→Loki), `otlp/tempo` (for traces→Tempo)
  - Pipelines: metrics→prometheus, logs→loki, traces→otlp/tempo
  - **Files**: `helm/cicd/values.yaml`, `helm/cicd/templates/otel-collector-deployment.yaml`, `helm/cicd/templates/otel-collector-configmap.yaml`

### 8.2 Add Prometheus
- [ ] Add to `values.yaml`: `prometheus` section (image: `prom/prometheus`, storage: 5Gi)
- [ ] Create `helm/cicd/templates/prometheus-statefulset.yaml`: StatefulSet + Service + PVC
- [ ] Create `helm/cicd/templates/prometheus-configmap.yaml`: scrape config:
  - Scrape server at `cicd-server:8080/actuator/prometheus`
  - Scrape worker at `cicd-worker:8081/actuator/prometheus`
  - Scrape interval: 15s
  - Enable exemplar storage
  - **Files**: `helm/cicd/values.yaml`, `helm/cicd/templates/prometheus-statefulset.yaml`, `helm/cicd/templates/prometheus-configmap.yaml`

### 8.3 Add Loki
- [ ] Add to `values.yaml`: `loki` section (image: `grafana/loki`, storage: 5Gi)
- [ ] Create `helm/cicd/templates/loki-statefulset.yaml`: StatefulSet + Service + PVC
- [ ] Create `helm/cicd/templates/loki-configmap.yaml`: Loki config (auth disabled, single-tenant, filesystem storage)
  - **Files**: `helm/cicd/values.yaml`, `helm/cicd/templates/loki-statefulset.yaml`, `helm/cicd/templates/loki-configmap.yaml`

### 8.4 Add Tempo
- [ ] Add to `values.yaml`: `tempo` section (image: `grafana/tempo`, storage: 5Gi)
- [ ] Create `helm/cicd/templates/tempo-statefulset.yaml`: StatefulSet + Service + PVC
- [ ] Create `helm/cicd/templates/tempo-configmap.yaml`: Tempo config (OTLP receiver, local filesystem backend)
  - **Files**: `helm/cicd/values.yaml`, `helm/cicd/templates/tempo-statefulset.yaml`, `helm/cicd/templates/tempo-configmap.yaml`

### 8.5 Add Grafana
- [ ] Add to `values.yaml`: `grafana` section (image: `grafana/grafana`, service type: ClusterIP or NodePort for access)
- [ ] Create `helm/cicd/templates/grafana-deployment.yaml`: Deployment + Service
- [ ] Create `helm/cicd/templates/grafana-configmap.yaml`: Contains:
  - Datasource provisioning (Prometheus, Loki, Tempo, PostgreSQL)
  - Dashboard provisioning config (points to dashboard JSON files)
  - **Files**: `helm/cicd/values.yaml`, `helm/cicd/templates/grafana-deployment.yaml`, `helm/cicd/templates/grafana-configmap.yaml`

### 8.6 Update existing Helm templates
- [ ] Update `helm/cicd/templates/server-deployment.yaml`:
  - Add env var `OTEL_EXPORTER_OTLP_ENDPOINT` pointing to `http://cicd-otel-collector:4317`
  - Add env var `OTEL_SERVICE_NAME=cicd-server`
  - Update liveness/readiness to use `/actuator/health` instead of TCP socket
  - **Files**: `helm/cicd/templates/server-deployment.yaml`

- [ ] Update `helm/cicd/templates/worker-deployment.yaml`:
  - Same OTel env vars with `OTEL_SERVICE_NAME=cicd-worker`
  - Update probes similarly
  - **Files**: `helm/cicd/templates/worker-deployment.yaml`

- [ ] Update `helm/cicd/templates/configmap.yaml`:
  - Add OTel-related config entries
  - **Files**: `helm/cicd/templates/configmap.yaml`

- [ ] Verify: `helm template cicd ./helm/cicd` renders without errors

### 8.7 Add health check probes for observability components
- [ ] Add liveness/readiness probes to all new StatefulSets/Deployments:
  - Prometheus: `/-/healthy` on port 9090
  - Loki: `/ready` on port 3100
  - Tempo: `/ready` on port 3200
  - Grafana: `/api/health` on port 3000
  - OTel Collector: `health_check` extension on port 13133
  - **Files**: respective template files created above

---

## Phase 9: Grafana Dashboards (Configuration as Code)

### 9.1 Dashboard 1: Pipeline Overview
- [ ] Create `helm/cicd/dashboards/pipeline-overview.json`:
  - Panel 1: Stat/bar chart — total pipeline runs by status (PromQL: `sum by (status) (cicd_pipeline_runs_total)`)
  - Panel 2: Time series — pipeline duration over time (PromQL: `histogram_quantile(0.95, rate(cicd_pipeline_duration_seconds_bucket[5m]))`)
  - Panel 3: Table — recent runs from PostgreSQL datasource (`SELECT pipeline_name, run_no, git_branch, git_hash, status, trace_id, start_time, end_time, EXTRACT(EPOCH FROM (end_time - start_time)) as duration_seconds FROM pipeline_runs ORDER BY start_time DESC LIMIT 20`)
  - Panel 3 should include a data link: click trace-id → open Dashboard 4 filtered to that trace
  - Template variables: time range picker
  - **Files**: `helm/cicd/dashboards/pipeline-overview.json`

### 9.2 Dashboard 2: Stage & Job Breakdown
- [ ] Create `helm/cicd/dashboards/stage-job-breakdown.json`:
  - Template variables: `pipeline` (dropdown from label values), `run_no` (from PostgreSQL query or label values)
  - Panel 1: Bar chart — per-stage duration (PromQL or PostgreSQL query)
  - Panel 2: Bar chart — per-job duration within selected pipeline+run
  - Panel 3: Stat — job success/failure counts (PromQL: `sum by (status) (cicd_job_runs_total{pipeline="$pipeline"})`)
  - **Files**: `helm/cicd/dashboards/stage-job-breakdown.json`

### 9.3 Dashboard 3: Logs Viewer
- [ ] Create `helm/cicd/dashboards/logs-viewer.json`:
  - Template variables: `pipeline`, `run_no`, `stage`, `job` (all from Loki label values)
  - Panel 1: Logs panel — LogQL query: `{pipeline="$pipeline", run_no="$run_no", stage=~"$stage", job=~"$job"}`
  - Must show both system logs (`source="system"`) and container logs (`source="job-container"`) with source distinguishable
  - Add `source` as a filterable template variable
  - **Files**: `helm/cicd/dashboards/logs-viewer.json`

### 9.4 Dashboard 4: Trace Explorer
- [ ] Create `helm/cicd/dashboards/trace-explorer.json`:
  - Template variable: `run_no`
  - Option A (recommended): Use Grafana's built-in Tempo trace viewer panel. Query Tempo by trace-id (fetched from PostgreSQL based on run_no).
  - Option B: Embed a direct link to Tempo's trace view UI
  - Panel: Traces panel showing full span hierarchy pipeline→stage→job with durations
  - **Files**: `helm/cicd/dashboards/trace-explorer.json`

### 9.5 Wire dashboards into Grafana provisioning
- [ ] Add dashboard JSON files as a ConfigMap in Helm:
  - Create `helm/cicd/templates/grafana-dashboards-configmap.yaml` that mounts all 4 JSON files
  - Reference in Grafana's provisioning config (`dashboards.yaml` provider pointing to `/var/lib/grafana/dashboards/`)
  - **Files**: `helm/cicd/templates/grafana-dashboards-configmap.yaml`, update `helm/cicd/templates/grafana-configmap.yaml`

---

## Phase 10: Prometheus Alert Rules (Extra Credit)

- [ ] Create `helm/cicd/templates/prometheus-rules-configmap.yaml`:
  - Rule 1: `CicdPipelineConsecutiveFailures` — alert if pipeline fails 3+ times in a row
  - Rule 2: `CicdJobDurationHigh` — alert if any job duration p95 > 10 minutes
  - Rule 3: `CicdPipelineDurationHigh` — alert if pipeline duration p95 > 30 minutes
  - Mount as additional config in Prometheus StatefulSet
  - **Files**: `helm/cicd/templates/prometheus-rules-configmap.yaml`, update `helm/cicd/templates/prometheus-configmap.yaml`

---

## Phase 11: Testing & Validation

### 11.1 Unit tests for new code
- [ ] Test `CicdMetrics`: verify counters increment, timers record, correct tags
  - **Files**: `server/src/test/java/cicd/observability/CicdMetricsTest.java`

- [ ] Test `TraceContextHelper`: verify inject/extract roundtrip with mock `MessageProperties`
  - **Files**: `common/src/test/java/cicd/observability/TraceContextHelperTest.java`

- [ ] Test trace-id in `ReportService`: verify traceId appears in DTOs
  - **Files**: `server/src/test/java/cicd/service/ReportServiceTest.java` (extend existing)

- [ ] Test `ReportCmd` output includes trace-id line
  - **Files**: `cli/src/test/java/cicd/cmd/ReportCmdTest.java` (extend existing)

### 11.2 Integration validation
- [ ] Deploy full stack via Helm, run a test pipeline, verify:
  - [ ] `curl server:8080/actuator/prometheus | grep cicd_` returns all 5 required metrics
  - [ ] Grafana Dashboard 1 shows pipeline runs
  - [ ] Grafana Dashboard 2 shows stage/job breakdown for a selected run
  - [ ] Grafana Dashboard 3 shows logs filterable by pipeline/run_no/stage/job, both system and container sources visible
  - [ ] Grafana Dashboard 4 shows trace hierarchy for a run
  - [ ] `cicd report --pipeline default --run 1` outputs trace-id field
  - [ ] Clicking trace-id in Dashboard 1 navigates to Dashboard 4
  - [ ] Prometheus alert rules are loaded (`curl prometheus:9090/api/v1/rules`)

---

## Phase 12: Documentation

- [ ] Update `README.md`:
  - Add Observability section explaining the stack
  - Document how to access Grafana (port-forward command)
  - Document dashboard descriptions
  - List the substitution justifications if any component was swapped
  - **Files**: `README.md`

- [ ] Update Helm `values.yaml` with inline comments for all new config options
  - **Files**: `helm/cicd/values.yaml`

---

## Summary of New Files to Create

```
server/src/main/resources/db/migration/V3__add_trace_id_to_pipeline_runs.sql
server/src/main/resources/logback-spring.xml
worker/src/main/resources/logback-spring.xml
server/src/main/java/cicd/config/OpenTelemetryConfig.java
worker/src/main/java/cicd/config/OpenTelemetryConfig.java
server/src/main/java/cicd/observability/CicdMetrics.java
common/src/main/java/cicd/observability/TraceContextHelper.java
helm/cicd/templates/otel-collector-deployment.yaml
helm/cicd/templates/otel-collector-configmap.yaml
helm/cicd/templates/prometheus-statefulset.yaml
helm/cicd/templates/prometheus-configmap.yaml
helm/cicd/templates/prometheus-rules-configmap.yaml
helm/cicd/templates/loki-statefulset.yaml
helm/cicd/templates/loki-configmap.yaml
helm/cicd/templates/tempo-statefulset.yaml
helm/cicd/templates/tempo-configmap.yaml
helm/cicd/templates/grafana-deployment.yaml
helm/cicd/templates/grafana-configmap.yaml
helm/cicd/templates/grafana-dashboards-configmap.yaml
helm/cicd/dashboards/pipeline-overview.json
helm/cicd/dashboards/stage-job-breakdown.json
helm/cicd/dashboards/logs-viewer.json
helm/cicd/dashboards/trace-explorer.json
server/src/test/java/cicd/observability/CicdMetricsTest.java
common/src/test/java/cicd/observability/TraceContextHelperTest.java
```

## Summary of Files to Modify

```
build.gradle (root) — no changes needed
server/build.gradle — add OTel, Micrometer, Actuator, Logstash deps
worker/build.gradle — add OTel, Logstash deps
common/build.gradle — add SLF4J, OTel API deps
server/src/main/resources/application.properties — add Actuator config
worker/src/main/resources/application.properties — add Actuator config
server/src/main/java/cicd/persistence/entity/PipelineRunEntity.java — add traceId
server/src/main/java/cicd/listener/StatusUpdateListener.java — metrics + traceId persistence + SLF4J
server/src/main/java/cicd/service/ReportService.java — populate traceId in DTOs
server/src/main/java/cicd/api/dto/PipelineRunDetailResponse.java — add traceId field
server/src/main/java/cicd/api/dto/RunSummaryDto.java — add traceId field
server/src/main/java/cicd/api/controller/PipelineExecutionController.java — create root span
server/src/main/java/cicd/config/RabbitMqConfig.java — add trace context post-processor
server/src/main/java/cicd/messaging/PipelineMessagePublisher.java — SLF4J
worker/src/main/java/cicd/config/RabbitMqConfig.java — add trace context post-processor
worker/src/main/java/cicd/listener/PipelineOrchestrationListener.java — tracing + SLF4J
worker/src/main/java/cicd/listener/JobExecutionListener.java — tracing + log forwarding + SLF4J
worker/src/main/java/cicd/listener/JobResultListener.java — SLF4J
worker/src/main/java/cicd/docker/LocalDockerRunner.java — stream logs via SLF4J
worker/src/main/java/cicd/service/StatusUpdatePublisher.java — inject traceId + SLF4J
worker/src/main/java/cicd/service/StatusEventPublisher.java — SLF4J
common/src/main/java/cicd/messaging/PipelineExecuteMessage.java — add traceId field
common/src/main/java/cicd/messaging/StatusUpdateMessage.java — add traceId field
cli/src/main/java/cicd/cmd/ReportCmd.java — print trace-id
helm/cicd/values.yaml — add observability component configs
helm/cicd/templates/server-deployment.yaml — add OTel env vars, update probes
helm/cicd/templates/worker-deployment.yaml — add OTel env vars, update probes
helm/cicd/templates/configmap.yaml — add OTel config entries
helm/cicd/Chart.yaml — bump version
README.md — add observability docs
```

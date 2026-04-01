# Week 8 Report

## Completed

> Note: All 10 issues are implemented on `feature/observability-phase1` but GitHub issues remain open because the branch has not been merged to main yet. The large scope of observability (touching server, worker, common, CLI, and Helm modules in 50+ commits) made it impractical to split into smaller PRs mid-development, so the merge is pending final review.

| Issue | Assignee | Estimate |
|-------|----------|----------|
| [#95. Add OpenTelemetry, Micrometer and logging dependencies](https://github.com/CS7580-SEA-SP26/d-team/issues/95) | @YskiGPG | - |
| [#96. Convert System.out to structured SLF4J JSON logging](https://github.com/CS7580-SEA-SP26/d-team/issues/96) | @YskiGPG | - |
| [#97. Configure OpenTelemetry SDK with OTLP exporter](https://github.com/CS7580-SEA-SP26/d-team/issues/97) | @YskiGPG | - |
| [#98. Add custom CI/CD metrics instrumentation](https://github.com/CS7580-SEA-SP26/d-team/issues/98) | @YskiGPG | - |
| [#99. Implement distributed tracing with W3C context propagation](https://github.com/CS7580-SEA-SP26/d-team/issues/99) | @YskiGPG | - |
| [#100. Forward Docker container logs to Loki via OTel and Add trace-id to report API and CLI output](https://github.com/CS7580-SEA-SP26/d-team/issues/100) | @YskiGPG | - |
| [#101. Add 4 Grafana dashboards as Configuration as Code](https://github.com/CS7580-SEA-SP26/d-team/issues/101) | @YskiGPG | - |
| [#102. Add Prometheus alert rules](https://github.com/CS7580-SEA-SP26/d-team/issues/102) | @YskiGPG | - |
| [#103. Add unit tests and integration validation](https://github.com/CS7580-SEA-SP26/d-team/issues/103) | @YskiGPG | 3pt |
| [#104. Fix dashboard data issues and enable DinD for K8s jobs](https://github.com/CS7580-SEA-SP26/d-team/issues/104) | @YskiGPG | 3pt |

## Carry Over

N/A

## What Worked

- Breaking observability into 10 fine-grained issues (#95–#104) gave clear ownership and trackable progress across dependencies, logging, metrics, tracing, dashboards, and alerting.
- Using OpenTelemetry Collector as a central telemetry hub simplified the architecture — apps only need one OTLP endpoint, and the collector fans out to Prometheus, Loki, and Tempo independently.
- Adopting Grafana dashboard-as-code (JSON ConfigMaps) ensures dashboards are version-controlled and reproducible across environments without manual setup.

## What Did Not Work
- Dashboard data issues (#104) surfaced late, requiring fixes to queries and enabling Docker-in-Docker for K8s job execution — indicating integration testing should have started earlier.

## Design Updates

- **Structured logging**: Replaced `System.out` with SLF4J + Logback JSON encoder (`logstash-logback-encoder`). Production profile outputs structured JSON with MDC fields (`pipeline`, `run_no`, `stage`, `job`, `source`). Logs forwarded to Loki via OpenTelemetry Collector.
- **Metrics**: Added 5 custom Micrometer metrics (`cicd_pipeline_runs_total`, `cicd_pipeline_duration_seconds`, `cicd_stage_duration_seconds`, `cicd_job_duration_seconds`, `cicd_job_runs_total`) exposed via Spring Actuator `/actuator/prometheus`. Recorded by `StatusUpdateListener`.
- **Distributed tracing**: W3C TraceContext propagated through RabbitMQ message headers using `TraceContextHelper`. Span hierarchy: pipeline > stage > job. `trace_id` stored in `pipeline_runs` table (new DB migration `V3`) and surfaced in CLI report output and API DTOs.
- **Observability infrastructure (Helm)**: New Helm templates for OTel Collector (ports 4317/4318), Prometheus (9090), Loki (3100), Tempo (3200), and Grafana (3000). All deployed as StatefulSets/Deployments with ConfigMap-based configuration.
- **Grafana dashboards**: 4 pre-built dashboards — Pipeline Overview (run status, duration, trace-id links), Stage & Job Breakdown (per-stage/job metrics), Logs Viewer (Loki-powered, filterable by pipeline/stage/job), Trace Explorer (Tempo-powered span visualization).
- **Prometheus alerts**: 3 alert rules — `CicdPipelineConsecutiveFailures` (critical, 3+ failures in 30min), `CicdJobDurationHigh` (warning, p95 > 10min), `CicdPipelineDurationHigh` (warning, p95 > 30min).

# Week 8 Report

## Completed

| Issue                                                                                                                                                          | Assignee   | Estimate |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------|------------|----------|
| [#95. Add OpenTelemetry, Micrometer and logging dependencies](https://github.com/CS7580-SEA-SP26/d-team/issues/95)                                            | @YskiGPG   | S        |
| [#96. Convert System.out to structured SLF4J JSON logging](https://github.com/CS7580-SEA-SP26/d-team/issues/96)                                               | @YskiGPG   | M        |
| [#97. Configure OpenTelemetry SDK with OTLP exporter](https://github.com/CS7580-SEA-SP26/d-team/issues/97)                                                    | @YskiGPG   | M        |
| [#98. Add custom CI/CD metrics instrumentation](https://github.com/CS7580-SEA-SP26/d-team/issues/98)                                                          | @YskiGPG   | M        |
| [#99. Implement distributed tracing with W3C context propagation](https://github.com/CS7580-SEA-SP26/d-team/issues/99)                                        | @YskiGPG   | L        |
| [#100. Forward Docker container logs to Loki via OTel and Add trace-id to report API and CLI output](https://github.com/CS7580-SEA-SP26/d-team/issues/100)    | @YskiGPG   | L        |
| [#101. Add 4 Grafana dashboards as Configuration as Code](https://github.com/CS7580-SEA-SP26/d-team/issues/101)                                               | @YskiGPG   | L        |
| [#102. Add Prometheus alert rules](https://github.com/CS7580-SEA-SP26/d-team/issues/102)                                                                      | @YskiGPG   | S        |
| [#103. Add unit tests and integration validation](https://github.com/CS7580-SEA-SP26/d-team/issues/103)                                                       | @YskiGPG   | M        |
| [#104. Fix dashboard data issues and enable DinD for K8s jobs](https://github.com/CS7580-SEA-SP26/d-team/issues/104)                                          | @YskiGPG   | M        |

## Carry Over

N/A

## What Worked

- Breaking the Observability Extension into phased implementation (dependencies → logging → OTel config → metrics → tracing → log forwarding → dashboards) allowed incremental development where each phase was independently compilable and testable before moving to the next.
- Using Configuration as Code for all Grafana dashboards and datasource provisioning ensured reproducibility — dashboards are automatically loaded on deployment with no manual UI setup.
- The OpenTelemetry Collector as a single ingestion point simplified the architecture: both server and worker emit telemetry to one endpoint, and the Collector routes metrics to Prometheus, logs to Loki, and traces to Tempo.

## What Did Not Work

- K8s worker pods do not have access to a Docker socket (k3s uses containerd), causing all pipeline job executions to fail. Resolved by adding a Docker-in-Docker (DinD) sidecar to the worker deployment (#104).
- Dashboard queries referencing Prometheus metrics required sufficient scrape history to render meaningful data — empty panels during initial testing caused confusion until enough pipeline runs were executed.

## Design Updates

- **Observability stack**: Deployed OTel Collector, Prometheus, Loki, Tempo, and Grafana via Helm alongside existing CI/CD services. All components have persistent storage and health check probes.
- **Metrics**: Server exposes 5 required CI/CD metrics via Micrometer + Spring Actuator `/actuator/prometheus` endpoint, scraped by Prometheus. Exemplars enabled for metrics-to-trace correlation.
- **Structured logging**: Replaced all `System.out.println` with SLF4J + Logstash JSON encoder. Logs include trace-id/span-id auto-injection for log-to-trace correlation. Job container stdout/stderr is captured and forwarded to Loki via OTel with pipeline/run_no/stage/job labels.
- **Distributed tracing**: OpenTelemetry SDK instruments the full execution path (pipeline → stage → job span hierarchy). Trace context propagates across RabbitMQ boundaries via W3C TraceContext headers injected into AMQP message properties.
- **Dashboards**: 4 provisioned Grafana dashboards — Pipeline Overview (with PostgreSQL-backed recent runs table), Stage & Job Breakdown, Logs Viewer (filterable by pipeline/run_no/stage/job/source), and Trace Explorer (Tempo waterfall view). Cross-linking between dashboards via trace-id data links.
- **Report command**: `cicd report --pipeline X --run N` now includes a `trace-id` field, stored in `pipeline_runs.trace_id` (new Flyway migration V3), enabling operators to correlate CLI output with traces in Grafana.
- **Bonus**: Prometheus alert rules (consecutive failures, duration thresholds, staleness check). DinD sidecar for K8s worker to enable Docker-based job execution in containerd environments.

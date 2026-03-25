# Week 7 Report

## Completed

| Issue                                                                                                                                              | Assignee      | Estimate |
|----------------------------------------------------------------------------------------------------------------------------------------------------|---------------|----------|
| [#84. Add StatusController with pipeline status endpoints](https://github.com/CS7580-SEA-SP26/d-team/issues/84)                                   | @julieyg      | S        |
| [#85. Add StatusService and repository query methods](https://github.com/CS7580-SEA-SP26/d-team/issues/85)                                        | @julieyg      | M        |
| [#86. Add status response DTOs with capitalized status strings](https://github.com/CS7580-SEA-SP26/d-team/issues/86)                              | @julieyg      | M        |
| [#87. Add unit tests for StatusService and StatusController](https://github.com/CS7580-SEA-SP26/d-team/issues/87)                                 | @julieyg      | M        |
| [#89. Create status subcommand](https://github.com/CS7580-SEA-SP26/d-team/issues/89)                                                             | @lietcooper   | L        |

## Carry Over
N/A

## What Worked

- Splitting the status feature into small, well-scoped issues (controller, service, DTOs, tests, CLI subcommand) allowed parallel work between @julieyg (server-side) and @lietcooper (CLI-side) with clean merge.
- The Helm chart (PR #91 by @YskiGPG) was developed independently and merged without conflicts, enabling Kubernetes deployment alongside the status feature work.

## What Did Not Work

- N/A

## Design Updates

- Added `status` subcommand to CLI that queries the server for pipeline/stage/job status via new REST endpoints.
- New server endpoints: `StatusController` with computed stage statuses, backed by `StatusService` and new repository query methods.
- Added Kubernetes Helm chart (`helm/cicd/`) for deploying server and worker with optional PostgreSQL and RabbitMQ.
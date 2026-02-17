# Week 3 Report

## Completed

| Issue | Assignee | Estimate |
|-------|----------|----------|
| [#32 [CLI] Implement run subcommand with argument parsing](https://github.com/CS7580-SEA-SP26/d-team/issues/32) | @Yange Wang | M |
| [#34 [Config] Implement pipeline lookup by name from .pipelines/ directory](https://github.com/CS7580-SEA-SP26/d-team/issues/34) | @Yange Wang | S |
| [#36 [Parser] Support list format in YAML job definitions](https://github.com/CS7580-SEA-SP26/d-team/issues/36) | @Yange Wang | S |
| [#33 [Git] Implement branch/commit validation for run subcommand](https://github.com/CS7580-SEA-SP26/d-team/issues/33) | @Yange Wang | M |
| [#37 Implement LocalDockerRunner](https://github.com/CS7580-SEA-SP26/d-team/issues/37) | @Lijun Wan | L |
| [#40 [Test] Add MockDockerRunner and fix RunCmd tests](https://github.com/CS7580-SEA-SP26/d-team/issues/40) | @Yange Wang | S |
| [#42 [Test] Add unit tests for executor package](https://github.com/CS7580-SEA-SP26/d-team/issues/42) | @Molly Yang | M |

## Carry Over

| Issue                                                        | Assignee                    | Estimate |
| ------------------------------------------------------------ | --------------------------- | -------- |
| [#38 [Design] Add additional remote validation on REST layer](https://github.com/CS7580-SEA-SP26/d-team/issues/38) | @Lijun Wan<br />@Molly Yang | M        |
| [#44 [Design] Revise architecture to message queue-driven design](https://github.com/CS7580-SEA-SP26/d-team/issues/44) | @Molly Yang                 | L        |

## What Worked

- Defined shared interfaces (`DockerRunner`, `JobResult`) on Day 1 and merged to main, allowing all three team members to develop independently on feature branches
- Reused `ExecutionPlanner` from Sprint 2 — the topological sort logic was already implemented, so `PipelineExecutor` just needed to call it
- Created `MockDockerRunner` for testing, which allowed us to write comprehensive unit tests without requiring Docker to be running
- Clear merge order (C → B → A) prevented integration conflicts: Docker implementation merged first, then execution engine, then CLI wiring

## What Did Not Work

- Some tests required capturing `System.out` and `System.err`, which added complexity to test setup

## Design Updates

- No major architecture changes this sprint — focused on implementation of the `run` subcommand for local execution
- Added `PipelineExecutor` class that orchestrates stage/job execution using the existing `ExecutionPlanner`
- Implemented `LocalDockerRunner` using `docker-java` library for real container execution
- Pipeline execution now prints clear progress output:
  ```cmd
  === Running pipeline: default ===
  --- Stage: build ---
    > Job: compile [gradle:jdk21-corretto]
    v Job 'compile' passed
  --- Stage: test ---
    > Job: statics [gradle:jdk21-corretto]
    x Job 'statics' failed (exit 1)
  === Pipeline FAILED ===
  ```

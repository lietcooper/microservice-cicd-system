# Week 6 Report

## Completed

| Issue                                                                                                                              | Assignee   | Estimate |
|------------------------------------------------------------------------------------------------------------------------------------|------------|----------|
| [#78. Add failures field to support allow-failure jobs in pipeline execution](https://github.com/CS7580-SEA-SP26/d-team/issues/78) | @Lijun Wan | L        |
| [#79. Draft example pipeline files for testing allow-failure feature](https://github.com/CS7580-SEA-SP26/d-team/issues/79) | @Lijun Wan | S        |
| [#80. Weekly report - sprint 6](https://github.com/CS7580-SEA-SP26/d-team/issues/80) | @Lijun Wan | S        |
## Carry Over
N/A

## What Worked

- Having a clear plan before implementation made the work efficient. The feature touches 12+ files across 4 modules (common, cli, worker, server), but because every change was scoped upfront, there were no surprises during coding.
- Implementing bottom-up (model -> parser -> messaging -> orchestration -> persistence -> report) avoided circular dependencies and made each layer testable before building the next.
- All 282 existing tests were updated and passing before any manual testing, which gave confidence that backward compatibility was preserved.

## What Did Not Work

- The `failures` × `needs` interaction rules required careful thought about edge cases (e.g., what happens when a skipped job is itself depended on by another job). This was not fully specified upfront and had to be reasoned through during implementation.

## Design Updates

- Added optional `failures` (boolean) key to pipeline YAML job definitions. When `failures: true`, a job is treated as optional — its failure does not affect stage/pipeline status.
- Changed execution semantics: required job failures now only skip **dependent** jobs (via `needs`), not all remaining jobs in the stage. Independent jobs in the same stage continue to run.
- Added `allow_failure` column to `job_runs` table (V2 migration) and surfaced it as `failures` in report API responses.

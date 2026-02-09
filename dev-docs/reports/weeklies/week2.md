# Week 2 Report

## Completed

| Issue                                                                                                               | Assignee | Estimate |
|---------------------------------------------------------------------------------------------------------------------|----------|----------|
| [#22 Implement dryrun subcommand framework](https://github.com/CS7580-SEA-SP26/d-team/issues/22)                    | @TODO | 2pt      |
| [#23 Implement execution order calculation (topological sort)](https://github.com/CS7580-SEA-SP26/d-team/issues/23) | @TODO | 2pt      |
| [#24 Implement dryrun formatted output and unit tests](https://github.com/CS7580-SEA-SP26/d-team/issues/23)         | @TODO | 1pt      |
| [#25 Create high-level design diagram](https://github.com/CS7580-SEA-SP26/d-team/issues/25)                         | @TODO | 3pt      |
| [#26 Document alternative designs](https://github.com/CS7580-SEA-SP26/d-team/issues/26)                             | @TODO | 2pt      |
| [#16 Create pipeline execution sequence diagram](https://github.com/CS7580-SEA-SP26/d-team/issues/16)               | @TODO | 2pt      |
| [#17 Create report request sequence diagram](https://github.com/CS7580-SEA-SP26/d-team/issues/17)                   | @TODO | 2pt      |
| [#18 Integrate sequence diagrams into design docs](https://github.com/CS7580-SEA-SP26/d-team/issues/18)             | @TODO | 1pt      |

## Carry Over

None

## What Worked

- Split Sprint 2 into three parallel workstreams (A: dryrun implementation, B: high-level design + alternatives, C: sequence diagrams) so team members could work independently without blocking each other
- Reused Sprint 1's YAML parser and validator in the dryrun command, which sped up implementation and ensured consistency
- Caught a potential bug early — duplicate `needs` entries could break topological sort; added a new validation rule to reject them at the validation layer instead of silently handling them in the planner
- All design documentation uses Mermaid diagrams embedded directly in Markdown, making them easy to version-control and review in PRs

## What Did Not Work

- Design documentation tasks were initially underestimated — writing clear architecture diagrams with proper pros/cons analysis took more thought than expected

## Design Updates

- Created comprehensive high-level design with Mermaid architecture diagram showing CLI, REST Service, DataStore, and Docker Engine components with communication protocols
- Documented two alternative designs considered by the team:
  1. Direct local execution (no REST layer) — rejected due to high migration cost to remote mode
  2. Message queue-driven architecture (RabbitMQ) — rejected as overkill for current scope
- Created sequence diagrams for:
  1. Pipeline execution happy path (`cicd run`)
  2. Report request flow (`cicd report`)
- Added a new validation rule: duplicate entries in a job's `needs` list are now rejected with a clear error message

# Week 1 Report

## Completed

| Issue | Assignee              | Estimate |
|-------|-----------------------|----------|
| [#1 Create initial high-level design document](https://github.com/CS7580-SEA-SP26/d-team/issues/1) | @Molly Yang           | 2pt |
| [#2 Create tech stack document](https://github.com/CS7580-SEA-SP26/d-team/issues/2) | @Molly Yang           | 2pt |
| [#3 Initialize CLI project with Gradle](https://github.com/CS7580-SEA-SP26/d-team/issues/3) | @Yange Wang           | 2pt |
| [#4 Implement YAML config parser](https://github.com/CS7580-SEA-SP26/d-team/issues/4) | @Yange Wang,Lijun Wan | 3pt |
| [#5 Implement YAML validation rules](https://github.com/CS7580-SEA-SP26/d-team/issues/5) | @Yange Wang           | 3pt |
| [#6 Implement error reporting format](https://github.com/CS7580-SEA-SP26/d-team/issues/6) | @Yange Wang,Lijun Wan       | 2pt |

## Carry Over
None

## What Worked

- Created all issues and assigned them 
- Issue #3 (project init) was completed first since other coding tasks depend on it
- Combined related work (#4 parser + #5 validation + #6 error reporting) into one PR to reduce merge conflicts
- Each PR was linked to its corresponding issue using `Closes #x`

## What Did Not Work

- Task dependencies were not clearly communicated upfront — teammates had to wait for #3 to merge before starting their coding work
- Some issues could have been broken down into smaller pieces for easier review

## Design Updates

- Decided on 3-component architecture: CLI, REST Service, DataStore
- CLI communicates with REST Service over HTTP
- Chose Gradle as build tool, picocli for CLI framework, snakeyaml for YAML parsing
- CI/CD workflow planning documented in tech-stack.md but actual implementation in Sprint 2

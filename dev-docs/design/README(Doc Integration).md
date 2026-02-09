# Design Documentation

This folder contains all system design documentation for our custom CI/CD system.

## Documents

| Document | Sprint | Description |
|----------|--------|-------------|
| [initial-design.md](initial-design.md) | Sprint 1 | Initial architecture overview with component descriptions and basic communication flow |
| [tech-stack.md](tech-stack.md) | Sprint 1 | Technology choices, build plugins, repository structure, and CI/CD pipeline configurations |
| [high-level-design.md](high-level-design.md) | Sprint 2 | Detailed high-level design with Mermaid diagram, communication summary, data model, deployment modes, and pros/cons |
| [sequence-diagrams.md](sequence-diagrams.md) | Sprint 2 | Sequence diagrams for pipeline execution (happy path) and report request flow, with step-by-step explanations |
| [alternative-designs.md](alternative-designs.md) | Sprint 2 | Two alternative architectures considered (direct local execution, message queue-driven) with diagrams, pros/cons, and rejection rationale |

## Sprint 2 Design Overview

Sprint 2 design documentation covers three areas:

1. **High-Level Design** -- A comprehensive architecture diagram showing all major components (CLI, REST Service, DataStore, Docker Engine), their communication protocols and directions, and the pros/cons of the chosen design.

2. **Sequence Diagrams** -- Two use case diagrams:
   - Pipeline execution (`cicd run`): shows the full happy path from CLI through REST Service, DataStore, and Docker Engine, covering stage/job lifecycle and status recording.
   - Report request (`cicd report`): shows how the CLI queries historical execution data through the REST Service and DataStore.

3. **Alternative Designs** -- Two alternatives the team evaluated:
   - Direct local execution without a REST middle layer (simpler but costly to migrate to remote mode).
   - Message queue-driven architecture with RabbitMQ (scalable but overkill for current requirements).

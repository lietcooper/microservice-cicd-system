# Tech Stack

## Overview

This document outlines the technologies, tools, and configurations used in our CI/CD system.

## Components Tech Stack

### CLI Component

| Category | Technology | Version | Purpose |
|----------|------------|---------|---------|
| Language | Java | 17 | Primary development language |
| Build Tool | Gradle | 8.x | Build automation and dependency management |
| CLI Framework | picocli | 4.7.x | Command line parsing and subcommands |
| YAML Parser | SnakeYAML | 2.x | Parse pipeline configuration files |
| Git Integration | JGit | 6.x | Interact with Git repositories |

### REST Service Component

| Category | Technology | Version | Purpose |
|----------|------------|---------|---------|
| Language | Java | 17 | Primary development language |
| Build Tool | Gradle | 8.x | Build automation and dependency management |
| Framework | Spring Boot | 3.x | REST API framework |
| Docker Client | docker-java | 3.x | Docker API integration |
| JSON Processing | Jackson | 2.x | JSON serialization/deserialization |

### DataStore Component

| Category | Technology | Version | Purpose |
|----------|------------|---------|---------|
| Database (Local) | SQLite | 3.x | File-based storage for local mode |
| Database (Remote) | PostgreSQL | 15.x | Persistent storage for remote mode |
| ORM | Spring Data JPA | 3.x | Database abstraction layer |
| Connection Pool | HikariCP | 5.x | Database connection pooling |

## Build Plugins

All components use the following Gradle plugins:

| Plugin | Purpose | Configuration |
|--------|---------|---------------|
| **JaCoCo** | Test coverage reporting | Minimum 80% line coverage |
| **Javadoc** | API documentation generation | Generated for all public APIs |
| **Checkstyle** | Code style enforcement | Google Java Style Guide |
| **SpotBugs** | Static bug analysis | High confidence bugs fail build |

### Gradle Build Configuration

```groovy
plugins {
    id 'java'
    id 'application'
    id 'jacoco'
    id 'checkstyle'
    id 'com.github.spotbugs' version '6.0.x'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

// JaCoCo Configuration
jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true
        html.required = true
    }
}

jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = 0.80  // 80% minimum coverage
            }
        }
    }
}

// Checkstyle Configuration
checkstyle {
    toolVersion = '10.12.x'
    configFile = file("config/checkstyle/checkstyle.xml")
}

// SpotBugs Configuration
spotbugs {
    effort = 'max'
    reportLevel = 'high'
}
```

## Repository Structure

### Main Repository: `d-team`

```
d-team/
├── .pipelines/                 # CI/CD configuration files
│   ├── pipeline.yaml          # Default pipeline
│   └── release.yaml           # Release pipeline
├── config/
│   └── checkstyle/
│       └── checkstyle.xml     # Checkstyle rules
├── dev-docs/
│   ├── design/
│   │   ├── initial-design.md  # Architecture documentation
│   │   └── tech-stack.md      # This document
│   └── reports/
│       └── weeklies/          # Weekly progress reports
├── src/
│   ├── main/java/cicd/
│   │   ├── App.java           # Application entry point
│   │   ├── cmd/               # CLI subcommands
│   │   ├── model/             # Data models
│   │   ├── parser/            # YAML parsing
│   │   ├── service/           # REST service (future)
│   │   └── validator/         # Validation logic
│   └── test/java/cicd/        # Unit tests
├── build.gradle               # Build configuration
├── settings.gradle            # Gradle settings
└── README.md                  # Project overview
```

## CI/CD Pipeline Configuration

We maintain three CI/CD pipelines for our development workflow:

### 1. Pull Request Pipeline (`pr.yml`)

Triggered on: Pull request creation and updates

```yaml
# .github/workflows/pr.yml
name: PR Checks

on:
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Build
        run: ./gradlew build
      
      - name: Run Tests
        run: ./gradlew test
      
      - name: Check Coverage
        run: ./gradlew jacocoTestCoverageVerification
      
      - name: Checkstyle
        run: ./gradlew checkstyleMain checkstyleTest
      
      - name: SpotBugs
        run: ./gradlew spotbugsMain
```

**Jobs:**
- `build` - Compile the project
- `test` - Run unit tests
- `coverage` - Verify minimum 80% test coverage
- `checkstyle` - Enforce code style
- `spotbugs` - Static analysis for bugs

### 2. Main Branch Pipeline (`main.yml`)

Triggered on: Push/merge to main branch

```yaml
# .github/workflows/main.yml
name: Main Build

on:
  push:
    branches: [main]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Build
        run: ./gradlew build
      
      - name: Run Tests with Coverage
        run: ./gradlew test jacocoTestReport
      
      - name: Upload Coverage Report
        uses: actions/upload-artifact@v4
        with:
          name: coverage-report
          path: build/reports/jacoco/
      
      - name: Checkstyle
        run: ./gradlew checkstyleMain checkstyleTest
      
      - name: SpotBugs
        run: ./gradlew spotbugsMain
      
      - name: Generate Javadoc
        run: ./gradlew javadoc
      
      - name: Upload Javadoc
        uses: actions/upload-artifact@v4
        with:
          name: javadoc
          path: build/docs/javadoc/
```

**Jobs:**
- All PR checks
- `javadoc` - Generate API documentation
- `upload-artifacts` - Store coverage and documentation

### 3. Release Pipeline (`release.yml`)

Triggered on: Tag creation (v*.*.*)

```yaml
# .github/workflows/release.yml
name: Release

on:
  push:
    tags:
      - 'v*.*.*'

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Build
        run: ./gradlew build
      
      - name: Run Tests
        run: ./gradlew test
      
      - name: Create Distribution
        run: ./gradlew assembleDist
      
      - name: Create Release
        uses: softprops/action-gh-release@v1
        with:
          files: build/distributions/*
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

**Jobs:**
- Full build and test
- `package` - Create distribution archive
- `release` - Publish GitHub release with artifacts

## Quality Requirements

### Test Coverage

| Metric | Minimum Requirement |
|--------|---------------------|
| Line Coverage | 80% |
| Branch Coverage | 70% |
| Class Coverage | 90% |

### Code Quality

| Check | Requirement |
|-------|-------------|
| Checkstyle | Zero violations |
| SpotBugs | Zero high-confidence bugs |
| Compiler Warnings | Zero warnings |

## Development Dependencies

### Required Software

| Software | Version | Purpose |
|----------|---------|---------|
| JDK | 17+ | Java development |
| Gradle | 8.x | Build tool (wrapper included) |
| Docker | 24.x+ | Container runtime |
| Git | 2.x+ | Version control |

### IDE Recommendations

- IntelliJ IDEA (recommended)
- VS Code with Java extensions
- Eclipse with Gradle plugin

## Future Considerations

- **Kubernetes Support**: Deploy REST Service to Kubernetes
- **Message Queue**: Add RabbitMQ for job queuing
- **Caching**: Add Redis for execution caching
- **Monitoring**: Integrate Prometheus/Grafana for metrics

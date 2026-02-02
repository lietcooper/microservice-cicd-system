# CICD System

Custom CI/CD system with local pipeline execution.

## Build

```bash
./gradlew build
```

## Run

```bash
./gradlew run --args="verify .pipelines/pipeline.yaml"
```

Or build jar and run:

```bash
./gradlew jar
java -jar build/libs/cicd-0.1.0.jar verify .pipelines/pipeline.yaml
```

## Test

```bash
./gradlew test
```

## Coverage Report

```bash
./gradlew jacocoTestReport
# Report at build/reports/jacoco/test/html/index.html
```

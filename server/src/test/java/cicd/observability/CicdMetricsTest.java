package cicd.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for CicdMetrics.
 * Verifies counters increment, timers record, and correct tags are applied.
 */
class CicdMetricsTest {

  private MeterRegistry registry;
  private CicdMetrics metrics;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    metrics = new CicdMetrics(registry);
  }

  @Test
  void recordPipelineCompletedIncrementsCounter() {
    metrics.recordPipelineCompleted("build-app", "SUCCESS", 5000);

    Counter counter = registry.find("cicd.pipeline.runs.total")
        .tag("pipeline", "build-app")
        .tag("status", "SUCCESS")
        .counter();

    assertNotNull(counter);
    assertEquals(1.0, counter.count());
  }

  @Test
  void recordPipelineCompletedRecordsDuration() {
    metrics.recordPipelineCompleted("build-app", "SUCCESS", 5000);

    Timer timer = registry.find("cicd.pipeline.duration.seconds")
        .tag("pipeline", "build-app")
        .timer();

    assertNotNull(timer);
    assertEquals(1, timer.count());
    assertEquals(5.0, timer.totalTime(java.util.concurrent.TimeUnit.SECONDS), 0.01);
  }

  @Test
  void recordPipelineCompletedMultipleTimesAccumulates() {
    metrics.recordPipelineCompleted("deploy", "SUCCESS", 3000);
    metrics.recordPipelineCompleted("deploy", "SUCCESS", 7000);

    Counter counter = registry.find("cicd.pipeline.runs.total")
        .tag("pipeline", "deploy")
        .tag("status", "SUCCESS")
        .counter();

    assertNotNull(counter);
    assertEquals(2.0, counter.count());
  }

  @Test
  void recordPipelineCompletedDistinguishesStatus() {
    metrics.recordPipelineCompleted("pipe", "SUCCESS", 1000);
    metrics.recordPipelineCompleted("pipe", "FAILED", 2000);

    Counter success = registry.find("cicd.pipeline.runs.total")
        .tag("pipeline", "pipe")
        .tag("status", "SUCCESS")
        .counter();
    Counter failed = registry.find("cicd.pipeline.runs.total")
        .tag("pipeline", "pipe")
        .tag("status", "FAILED")
        .counter();

    assertNotNull(success);
    assertNotNull(failed);
    assertEquals(1.0, success.count());
    assertEquals(1.0, failed.count());
  }

  @Test
  void recordStageCompletedRecordsDuration() {
    metrics.recordStageCompleted("build-app", "compile", 2000);

    Timer timer = registry.find("cicd.stage.duration.seconds")
        .tag("pipeline", "build-app")
        .tag("stage", "compile")
        .timer();

    assertNotNull(timer);
    assertEquals(1, timer.count());
    assertEquals(2.0, timer.totalTime(java.util.concurrent.TimeUnit.SECONDS), 0.01);
  }

  @Test
  void recordStageCompletedDistinguishesStages() {
    metrics.recordStageCompleted("pipe", "build", 1000);
    metrics.recordStageCompleted("pipe", "test", 3000);

    Timer buildTimer = registry.find("cicd.stage.duration.seconds")
        .tag("pipeline", "pipe")
        .tag("stage", "build")
        .timer();
    Timer testTimer = registry.find("cicd.stage.duration.seconds")
        .tag("pipeline", "pipe")
        .tag("stage", "test")
        .timer();

    assertNotNull(buildTimer);
    assertNotNull(testTimer);
    assertEquals(1.0, buildTimer.totalTime(java.util.concurrent.TimeUnit.SECONDS), 0.01);
    assertEquals(3.0, testTimer.totalTime(java.util.concurrent.TimeUnit.SECONDS), 0.01);
  }

  @Test
  void recordJobCompletedIncrementsCounter() {
    metrics.recordJobCompleted("pipe", "build", "compile", "SUCCESS", 1500);

    Counter counter = registry.find("cicd.job.runs.total")
        .tag("pipeline", "pipe")
        .tag("stage", "build")
        .tag("job", "compile")
        .tag("status", "SUCCESS")
        .counter();

    assertNotNull(counter);
    assertEquals(1.0, counter.count());
  }

  @Test
  void recordJobCompletedRecordsDuration() {
    metrics.recordJobCompleted("pipe", "build", "compile", "SUCCESS", 1500);

    Timer timer = registry.find("cicd.job.duration.seconds")
        .tag("pipeline", "pipe")
        .tag("stage", "build")
        .tag("job", "compile")
        .timer();

    assertNotNull(timer);
    assertEquals(1, timer.count());
    assertEquals(1.5, timer.totalTime(java.util.concurrent.TimeUnit.SECONDS), 0.01);
  }

  @Test
  void recordJobCompletedDistinguishesStatus() {
    metrics.recordJobCompleted("pipe", "build", "compile", "SUCCESS", 1000);
    metrics.recordJobCompleted("pipe", "build", "compile", "FAILED", 2000);

    Counter success = registry.find("cicd.job.runs.total")
        .tag("pipeline", "pipe")
        .tag("stage", "build")
        .tag("job", "compile")
        .tag("status", "SUCCESS")
        .counter();
    Counter failed = registry.find("cicd.job.runs.total")
        .tag("pipeline", "pipe")
        .tag("stage", "build")
        .tag("job", "compile")
        .tag("status", "FAILED")
        .counter();

    assertNotNull(success);
    assertNotNull(failed);
    assertEquals(1.0, success.count());
    assertEquals(1.0, failed.count());
  }

  @Test
  void recordJobCompletedDistinguishesJobs() {
    metrics.recordJobCompleted("pipe", "build", "compile", "SUCCESS", 1000);
    metrics.recordJobCompleted("pipe", "build", "lint", "SUCCESS", 500);

    Counter compileCounter = registry.find("cicd.job.runs.total")
        .tag("job", "compile")
        .counter();
    Counter lintCounter = registry.find("cicd.job.runs.total")
        .tag("job", "lint")
        .counter();

    assertNotNull(compileCounter);
    assertNotNull(lintCounter);
    assertEquals(1.0, compileCounter.count());
    assertEquals(1.0, lintCounter.count());
  }
}

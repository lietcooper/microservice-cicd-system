package cicd.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Central metrics service for CI/CD observability.
 * Registers and records all required Micrometer metrics.
 */
@Component
public class CicdMetrics {

  private final MeterRegistry registry;

  /**
   * Creates the metrics service with the given registry.
   *
   * @param registry the Micrometer meter registry
   */
  public CicdMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  /**
   * Records a completed pipeline run.
   *
   * @param pipeline pipeline name
   * @param status   final status (SUCCESS or FAILED)
   * @param durationMs duration in milliseconds
   */
  public void recordPipelineCompleted(String pipeline, String status,
      long durationMs) {
    Counter.builder("cicd.pipeline.runs.total")
        .tag("pipeline", pipeline)
        .tag("status", status)
        .register(registry)
        .increment();

    Timer.builder("cicd.pipeline.duration.seconds")
        .tag("pipeline", pipeline)
        .register(registry)
        .record(Duration.ofMillis(durationMs));
  }

  /**
   * Records a completed stage.
   *
   * @param pipeline pipeline name
   * @param stage    stage name
   * @param durationMs duration in milliseconds
   */
  public void recordStageCompleted(String pipeline, String stage,
      long durationMs) {
    Timer.builder("cicd.stage.duration.seconds")
        .tag("pipeline", pipeline)
        .tag("stage", stage)
        .register(registry)
        .record(Duration.ofMillis(durationMs));
  }

  /**
   * Records a completed job.
   *
   * @param pipeline pipeline name
   * @param stage    stage name
   * @param job      job name
   * @param status   final status (SUCCESS or FAILED)
   * @param durationMs duration in milliseconds
   */
  public void recordJobCompleted(String pipeline, String stage, String job,
      String status, long durationMs) {
    Counter.builder("cicd.job.runs.total")
        .tag("pipeline", pipeline)
        .tag("stage", stage)
        .tag("job", job)
        .tag("status", status)
        .register(registry)
        .increment();

    Timer.builder("cicd.job.duration.seconds")
        .tag("pipeline", pipeline)
        .tag("stage", stage)
        .tag("job", job)
        .register(registry)
        .record(Duration.ofMillis(durationMs));
  }
}

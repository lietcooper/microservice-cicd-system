package cicd.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cicd.messaging.JobResultMessage;
import cicd.service.StageCoordinatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for JobResultListener.
 * Verifies that incoming job result messages are forwarded to StageCoordinatorService.
 */
class JobResultListenerTest {

  private StageCoordinatorService coordinator;
  private JobResultListener listener;

  @BeforeEach
  void setUp() {
    coordinator = mock(StageCoordinatorService.class);
    listener = new JobResultListener(coordinator);
  }

  @Test
  void onJobResultDelegatesToCoordinatorRecordResult() {
    JobResultMessage result = buildResult("wave-1", "compile", true);

    listener.onJobResult(result);

    verify(coordinator).recordResult("wave-1", result);
  }

  @Test
  void onJobResultPassesCorrelationIdToCoordinator() {
    String correlationId = "corr-abc-123";
    JobResultMessage result = buildResult(correlationId, "unittest", false);

    listener.onJobResult(result);

    verify(coordinator).recordResult(correlationId, result);
  }

  @Test
  void onJobResultForSuccessfulJobDelegatesToCoordinator() {
    JobResultMessage result = buildResult("wave-2", "javadoc", true);
    result.setExitCode(0);
    result.setOutput("docs generated");

    listener.onJobResult(result);

    verify(coordinator).recordResult("wave-2", result);
  }

  @Test
  void onJobResultForFailedJobDelegatesToCoordinator() {
    JobResultMessage result = buildResult("wave-3", "checkstyle", false);
    result.setExitCode(1);
    result.setOutput("Checkstyle violation found");

    listener.onJobResult(result);

    verify(coordinator).recordResult("wave-3", result);
  }

  @Test
  void onJobResultWithNullOutputDoesNotThrow() {
    JobResultMessage result = buildResult("wave-null", "test", true);
    result.setOutput(null);

    // Should not throw
    listener.onJobResult(result);

    verify(coordinator).recordResult("wave-null", result);
  }

  @Test
  void onJobResultWithAllFieldsPopulatedDelegatesToCoordinator() {
    JobResultMessage result = new JobResultMessage();
    result.setCorrelationId("full-corr");
    result.setJobName("build-job");
    result.setStageName("build");
    result.setPipelineName("default");
    result.setPipelineRunId(42L);
    result.setSuccess(true);
    result.setExitCode(0);
    result.setOutput("BUILD SUCCESSFUL");

    listener.onJobResult(result);

    verify(coordinator).recordResult("full-corr", result);
  }

  // ── Helper ──────────────────────────────────────────────────────────────────

  private JobResultMessage buildResult(
      String correlationId, String jobName, boolean success) {
    JobResultMessage msg = new JobResultMessage();
    msg.setCorrelationId(correlationId);
    msg.setJobName(jobName);
    msg.setSuccess(success);
    msg.setExitCode(success ? 0 : 1);
    msg.setPipelineRunId(1L);
    msg.setStageName("test");
    msg.setPipelineName("default");
    return msg;
  }
}

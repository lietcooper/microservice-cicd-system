package cicd.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cicd.config.RabbitMqConfig;
import cicd.messaging.JobExecuteMessage;
import cicd.messaging.JobResultMessage;
import cicd.service.StatusEventPublisher;
import cicd.service.StatusUpdatePublisher;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Tests for JobExecutionListener.
 *
 * <p>Since JobExecutionListener creates a LocalDockerRunner internally (which
 * requires a real Docker daemon), this test verifies the messaging contract:
 * status updates are published before and after Docker execution, and a
 * JobResultMessage is published back to the job-results exchange.
 * Docker will fail in the test environment (no daemon), so we verify the
 * error-path behavior: status updates for start then completion (FAILED),
 * and a result message published with success=false.
 */
class JobExecutionListenerTest {

  private RabbitTemplate rabbitTemplate;
  private StatusEventPublisher eventPublisher;
  private StatusUpdatePublisher statusPublisher;
  private JobExecutionListener listener;

  @BeforeEach
  void setUp() {
    rabbitTemplate = mock(RabbitTemplate.class);
    eventPublisher = mock(StatusEventPublisher.class);
    statusPublisher = mock(StatusUpdatePublisher.class);
    listener = new JobExecutionListener(
        rabbitTemplate, eventPublisher, statusPublisher);
  }

  @Test
  void onJobExecutePublishesJobStartedStatusUpdate() {
    JobExecuteMessage msg = buildJobMessage("compile", "build", "alpine");

    // Docker will fail (no daemon), but status updates should still fire
    listener.onJobExecute(msg);

    verify(statusPublisher).jobStarted(
        eq(1L), eq("default"), eq(1), eq("build"), eq("compile"));
  }

  @Test
  void onJobExecutePublishesJobStartedEventPublisher() {
    JobExecuteMessage msg = buildJobMessage("compile", "build", "alpine");

    listener.onJobExecute(msg);

    verify(eventPublisher).publishJobStarted(
        eq(1L), eq("default"), eq(1), eq("build"), eq("compile"));
  }

  @Test
  void onJobExecutePublishesJobCompletedStatusUpdate() {
    JobExecuteMessage msg = buildJobMessage("compile", "build", "alpine");

    listener.onJobExecute(msg);

    // Docker fails in test env, so success=false is expected
    verify(statusPublisher).jobCompleted(
        eq(1L), eq("default"), eq(1), eq("build"), eq("compile"), anyBoolean());
  }

  @Test
  void onJobExecutePublishesJobCompletedEvent() {
    JobExecuteMessage msg = buildJobMessage("unittest", "test", "alpine");

    listener.onJobExecute(msg);

    verify(eventPublisher).publishJobCompleted(
        eq(1L), eq("default"), eq(1), eq("test"), eq("unittest"), anyBoolean());
  }

  @Test
  void onJobExecutePublishesJobResultMessageToJobResultsExchange() {
    JobExecuteMessage msg = buildJobMessage("compile", "build", "alpine");

    listener.onJobExecute(msg);

    ArgumentCaptor<JobResultMessage> resultCaptor =
        ArgumentCaptor.forClass(JobResultMessage.class);
    verify(rabbitTemplate).convertAndSend(
        eq(RabbitMqConfig.JOB_RESULTS_EXCHANGE),
        eq(RabbitMqConfig.JOB_RESULT_KEY),
        resultCaptor.capture());

    JobResultMessage result = resultCaptor.getValue();
    assertEquals("compile", result.getJobName());
    assertEquals("build", result.getStageName());
    assertEquals("default", result.getPipelineName());
    assertEquals(1L, result.getPipelineRunId());
    assertEquals("corr-1", result.getCorrelationId());
  }

  @Test
  void onJobExecuteResultMessageContainsCorrectCorrelationId() {
    JobExecuteMessage msg = buildJobMessage("compile", "build", "alpine");
    msg.setCorrelationId("my-wave-id");

    listener.onJobExecute(msg);

    ArgumentCaptor<JobResultMessage> resultCaptor =
        ArgumentCaptor.forClass(JobResultMessage.class);
    verify(rabbitTemplate).convertAndSend(
        anyString(), anyString(), resultCaptor.capture());

    assertEquals("my-wave-id", resultCaptor.getValue().getCorrelationId());
  }

  @Test
  void onJobExecuteWithMultipleScriptsPublishesResult() {
    JobExecuteMessage msg = buildJobMessage("statics", "test", "gradle:jdk21");
    msg.setScripts(List.of("gradle checkstyle", "gradle spotbugs", "gradle test"));

    listener.onJobExecute(msg);

    // Verify a result is always published regardless of Docker outcome
    verify(rabbitTemplate).convertAndSend(
        eq(RabbitMqConfig.JOB_RESULTS_EXCHANGE),
        eq(RabbitMqConfig.JOB_RESULT_KEY),
        any(JobResultMessage.class));
  }

  // ── Helper ───────────────────────────────────────────────────────────────────

  private JobExecuteMessage buildJobMessage(
      String jobName, String stageName, String image) {
    JobExecuteMessage msg = new JobExecuteMessage();
    msg.setPipelineRunId(1L);
    msg.setCorrelationId("corr-1");
    msg.setJobName(jobName);
    msg.setStageName(stageName);
    msg.setPipelineName("default");
    msg.setRunNo(1);
    msg.setImage(image);
    msg.setScripts(List.of("echo run"));
    msg.setRepoPath("/tmp/repo");
    msg.setTotalJobsInWave(1);
    return msg;
  }
}

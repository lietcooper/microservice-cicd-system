package cicd.listener;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cicd.config.RabbitMqConfig;
import cicd.messaging.JobExecuteMessage;
import cicd.messaging.PipelineExecuteMessage;
import cicd.service.StageCoordinatorService;
import cicd.service.StatusEventPublisher;
import cicd.service.StatusUpdatePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Tests for PipelineOrchestrationListener.
 * Uses Mockito to verify the orchestrator:
 * - Publishes status updates at each lifecycle stage
 * - Dispatches JobExecuteMessages to the job exchange
 * - Handles invalid YAML without throwing
 * - Handles wave failure by stopping further execution
 */
class PipelineOrchestrationListenerTest {

  private RabbitTemplate rabbitTemplate;
  private StageCoordinatorService coordinator;
  private StatusEventPublisher eventPublisher;
  private StatusUpdatePublisher statusPublisher;
  private PipelineOrchestrationListener listener;

  @BeforeEach
  void setUp() {
    rabbitTemplate = mock(RabbitTemplate.class);
    coordinator = mock(StageCoordinatorService.class);
    eventPublisher = mock(StatusEventPublisher.class);
    statusPublisher = mock(StatusUpdatePublisher.class);
    listener = new PipelineOrchestrationListener(
        rabbitTemplate, coordinator, eventPublisher, statusPublisher);
  }

  // ── YAML parse failure path ─────────────────────────────────────────────────

  @Test
  void onPipelineExecuteWithInvalidYamlPublishesPipelineFailed() {
    PipelineExecuteMessage msg = buildMsg("invalid-yaml-!!!::: broken");

    listener.onPipelineExecute(msg);

    verify(statusPublisher).pipelineFailed(
        eq(1L), eq("default"), eq(1));
  }

  @Test
  void onPipelineExecuteWithInvalidYamlDoesNotDispatchJobs() {
    PipelineExecuteMessage msg = buildMsg("not: valid: yaml: ::::");

    listener.onPipelineExecute(msg);

    verify(rabbitTemplate, never()).convertAndSend(
        eq(RabbitMqConfig.JOB_EXCHANGE),
        eq(RabbitMqConfig.JOB_EXECUTE_KEY),
        any(JobExecuteMessage.class));
  }

  @Test
  void onPipelineExecuteWithInvalidYamlPublishesRunningFirst() {
    PipelineExecuteMessage msg = buildMsg("invalid");

    listener.onPipelineExecute(msg);

    // Running status is published before parse failure
    verify(statusPublisher).pipelineRunning(eq(1L), eq("default"), eq(1));
    verify(statusPublisher).pipelineFailed(eq(1L), eq("default"), eq(1));
  }

  // ── Valid YAML – happy path ─────────────────────────────────────────────────

  @Test
  void onPipelineExecuteWithValidYamlPublishesPipelineRunning() {
    PipelineExecuteMessage msg = buildMsg(singleJobYaml());
    setupSuccessfulWave();

    listener.onPipelineExecute(msg);

    verify(statusPublisher).pipelineRunning(eq(1L), eq("default"), eq(1));
  }

  @Test
  void onPipelineExecuteWithValidYamlPublishesPipelineStartedEvent() {
    PipelineExecuteMessage msg = buildMsg(singleJobYaml());
    setupSuccessfulWave();

    listener.onPipelineExecute(msg);

    verify(eventPublisher).publishPipelineStarted(eq(1L), eq("default"), eq(1));
  }

  @Test
  void onPipelineExecuteWithValidYamlDispatchesJobToExchange() {
    PipelineExecuteMessage msg = buildMsg(singleJobYaml());
    setupSuccessfulWave();

    listener.onPipelineExecute(msg);

    ArgumentCaptor<JobExecuteMessage> jobCaptor =
        ArgumentCaptor.forClass(JobExecuteMessage.class);
    verify(rabbitTemplate).convertAndSend(
        eq(RabbitMqConfig.JOB_EXCHANGE),
        eq(RabbitMqConfig.JOB_EXECUTE_KEY),
        jobCaptor.capture());

    JobExecuteMessage dispatchedJob = jobCaptor.getValue();
    assertNotNull(dispatchedJob);
    assertEquals("compile", dispatchedJob.getJobName());
    assertEquals("build", dispatchedJob.getStageName());
    assertEquals("alpine", dispatchedJob.getImage());
    assertEquals("default", dispatchedJob.getPipelineName());
    assertEquals(1L, dispatchedJob.getPipelineRunId());
    assertEquals(1, dispatchedJob.getRunNo());
  }

  @Test
  void onPipelineExecuteWithValidYamlPublishesJobCreatedBeforeDispatching() {
    PipelineExecuteMessage msg = buildMsg(singleJobYaml());
    setupSuccessfulWave();

    listener.onPipelineExecute(msg);

    verify(statusPublisher).jobCreated(
        eq(1L), eq("default"), eq(1), eq("build"), eq("compile"));
  }

  @Test
  void onPipelineExecuteWithValidYamlPublishesStageStarted() {
    PipelineExecuteMessage msg = buildMsg(singleJobYaml());
    setupSuccessfulWave();

    listener.onPipelineExecute(msg);

    verify(statusPublisher).stageStarted(
        eq(1L), eq("default"), eq(1), eq("build"), anyInt());
  }

  @Test
  void onPipelineExecuteWithValidYamlPublishesStageStartedEvent() {
    PipelineExecuteMessage msg = buildMsg(singleJobYaml());
    setupSuccessfulWave();

    listener.onPipelineExecute(msg);

    verify(eventPublisher).publishStageStarted(
        eq(1L), eq("default"), eq(1), eq("build"));
  }

  @Test
  void onPipelineExecuteSuccessPublishesPipelineCompletedTrue() {
    PipelineExecuteMessage msg = buildMsg(singleJobYaml());
    setupSuccessfulWave();

    listener.onPipelineExecute(msg);

    verify(statusPublisher).pipelineCompleted(
        eq(1L), eq("default"), eq(1), eq(true));
  }

  @Test
  void onPipelineExecuteSuccessPublishesPipelineCompletedEvent() {
    PipelineExecuteMessage msg = buildMsg(singleJobYaml());
    setupSuccessfulWave();

    listener.onPipelineExecute(msg);

    verify(eventPublisher).publishPipelineCompleted(
        eq(1L), eq("default"), eq(1), eq(true));
  }

  @Test
  void onPipelineExecuteSuccessPublishesStageCompleted() {
    PipelineExecuteMessage msg = buildMsg(singleJobYaml());
    setupSuccessfulWave();

    listener.onPipelineExecute(msg);

    verify(statusPublisher).stageCompleted(
        eq(1L), eq("default"), eq(1), eq("build"), eq(true));
  }

  // ── Failed wave path ─────────────────────────────────────────────────────────

  @Test
  void onPipelineExecuteWithFailedWavePublishesPipelineCompletedFalse() {
    PipelineExecuteMessage msg = buildMsg(singleJobYaml());
    setupFailedWave();

    listener.onPipelineExecute(msg);

    verify(statusPublisher).pipelineCompleted(
        eq(1L), eq("default"), eq(1), eq(false));
  }

  @Test
  void onPipelineExecuteWithFailedWavePublishesStageCompletedFalse() {
    PipelineExecuteMessage msg = buildMsg(singleJobYaml());
    setupFailedWave();

    listener.onPipelineExecute(msg);

    verify(statusPublisher).stageCompleted(
        eq(1L), eq("default"), eq(1), eq("build"), eq(false));
  }

  @Test
  void onPipelineExecuteWithNullWaveTrackerPublishesPipelineFailed() {
    PipelineExecuteMessage msg = buildMsg(singleJobYaml());
    // awaitWave returns null (unknown correlation)
    when(coordinator.awaitWave(anyString())).thenReturn(null);

    listener.onPipelineExecute(msg);

    verify(statusPublisher).pipelineCompleted(
        eq(1L), eq("default"), eq(1), eq(false));
  }

  @Test
  void onPipelineExecuteRegistersWaveWithCoordinator() {
    PipelineExecuteMessage msg = buildMsg(singleJobYaml());
    setupSuccessfulWave();

    listener.onPipelineExecute(msg);

    // Coordinator should have had a wave registered
    verify(coordinator, atLeastOnce()).registerWave(anyString(), eq(1));
  }

  @Test
  void onPipelineExecuteRemovesWaveAfterCompletion() {
    PipelineExecuteMessage msg = buildMsg(singleJobYaml());
    setupSuccessfulWave();

    listener.onPipelineExecute(msg);

    verify(coordinator, atLeastOnce()).removeWave(anyString());
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  private void assertEquals(Object expected, Object actual) {
    org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
  }

  /** Sets up coordinator to return a successful WaveTracker. */
  private void setupSuccessfulWave() {
    StageCoordinatorService.WaveTracker tracker =
        new StageCoordinatorService.WaveTracker(1);
    tracker.addResult(buildSuccessResult());
    tracker.countDown();
    when(coordinator.awaitWave(anyString())).thenReturn(tracker);
  }

  /** Sets up coordinator to return a failed WaveTracker. */
  private void setupFailedWave() {
    cicd.messaging.JobResultMessage failResult = new cicd.messaging.JobResultMessage();
    failResult.setJobName("compile");
    failResult.setSuccess(false);
    failResult.setExitCode(1);
    StageCoordinatorService.WaveTracker tracker =
        new StageCoordinatorService.WaveTracker(1);
    tracker.addResult(failResult);
    tracker.countDown();
    when(coordinator.awaitWave(anyString())).thenReturn(tracker);
  }

  private cicd.messaging.JobResultMessage buildSuccessResult() {
    cicd.messaging.JobResultMessage result = new cicd.messaging.JobResultMessage();
    result.setJobName("compile");
    result.setSuccess(true);
    result.setExitCode(0);
    return result;
  }

  private PipelineExecuteMessage buildMsg(String yaml) {
    return new PipelineExecuteMessage(
        1L, "default", 1, yaml, "/tmp/repo", "main", "abc123");
  }

  /** Minimal valid YAML with one job in a 'build' stage. */
  private String singleJobYaml() {
    return "pipeline:\n"
        + "  name: default\n"
        + "stages:\n"
        + "  - build\n"
        + "compile:\n"
        + "  stage: build\n"
        + "  image: alpine\n"
        + "  script: echo hello\n";
  }
}

package cicd.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import cicd.messaging.StatusUpdateMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class StatusUpdatePublisherTest {

  private RabbitTemplate rabbitTemplate;
  private StatusUpdatePublisher publisher;

  @BeforeEach
  void setUp() {
    rabbitTemplate = mock(RabbitTemplate.class);
    publisher = new StatusUpdatePublisher(rabbitTemplate);
  }

  // ── pipelineRunning ────────────────────────────────

  @Test
  void pipelineRunningPublishesEntityTypePipeline() {
    publisher.pipelineRunning(1L, "default", 3);

    ArgumentCaptor<StatusUpdateMessage> captor =
        ArgumentCaptor.forClass(StatusUpdateMessage.class);
    verify(rabbitTemplate).convertAndSend(
        anyString(), anyString(), captor.capture());

    StatusUpdateMessage msg = captor.getValue();
    assertEquals("PIPELINE", msg.getEntityType());
    assertEquals(1L, msg.getPipelineRunId());
    assertEquals("default", msg.getPipelineName());
    assertEquals(3, msg.getRunNo());
    assertEquals("RUNNING", msg.getStatus());
    assertNotNull(msg.getStartTime());
    assertNull(msg.getEndTime());
  }

  // ── pipelineFailed ─────────────────────────────────

  @Test
  void pipelineFailedPublishesFailedStatus() {
    publisher.pipelineFailed(2L, "mypipe", 1);

    ArgumentCaptor<StatusUpdateMessage> captor =
        ArgumentCaptor.forClass(StatusUpdateMessage.class);
    verify(rabbitTemplate).convertAndSend(
        anyString(), anyString(), captor.capture());

    StatusUpdateMessage msg = captor.getValue();
    assertEquals("PIPELINE", msg.getEntityType());
    assertEquals("FAILED", msg.getStatus());
    assertNotNull(msg.getEndTime());
    assertNull(msg.getStartTime());
  }

  // ── pipelineCompleted ──────────────────────────────

  @Test
  void pipelineCompletedSuccessPublishesSuccessStatus() {
    publisher.pipelineCompleted(3L, "mypipe", 2, true);

    ArgumentCaptor<StatusUpdateMessage> captor =
        ArgumentCaptor.forClass(StatusUpdateMessage.class);
    verify(rabbitTemplate).convertAndSend(
        anyString(), anyString(), captor.capture());

    assertEquals("SUCCESS",
        captor.getValue().getStatus());
    assertNotNull(captor.getValue().getEndTime());
  }

  @Test
  void pipelineCompletedFailurePublishesFailedStatus() {
    publisher.pipelineCompleted(3L, "mypipe", 2, false);

    ArgumentCaptor<StatusUpdateMessage> captor =
        ArgumentCaptor.forClass(StatusUpdateMessage.class);
    verify(rabbitTemplate).convertAndSend(
        anyString(), anyString(), captor.capture());

    assertEquals("FAILED",
        captor.getValue().getStatus());
  }

  // ── stageStarted ───────────────────────────────────

  @Test
  void stageStartedPublishesEntityTypeStage() {
    publisher.stageStarted(1L, "pipe", 1, "build", 0);

    ArgumentCaptor<StatusUpdateMessage> captor =
        ArgumentCaptor.forClass(StatusUpdateMessage.class);
    verify(rabbitTemplate).convertAndSend(
        anyString(), anyString(), captor.capture());

    StatusUpdateMessage msg = captor.getValue();
    assertEquals("STAGE", msg.getEntityType());
    assertEquals("build", msg.getStageName());
    assertEquals(0, msg.getStageOrder());
    assertEquals("RUNNING", msg.getStatus());
    assertNotNull(msg.getStartTime());
  }

  // ── stageCompleted ─────────────────────────────────

  @Test
  void stageCompletedSuccessPublishesSuccessStatus() {
    publisher.stageCompleted(
        1L, "pipe", 1, "build", true);

    ArgumentCaptor<StatusUpdateMessage> captor =
        ArgumentCaptor.forClass(StatusUpdateMessage.class);
    verify(rabbitTemplate).convertAndSend(
        anyString(), anyString(), captor.capture());

    assertEquals("SUCCESS",
        captor.getValue().getStatus());
    assertNotNull(captor.getValue().getEndTime());
  }

  @Test
  void stageCompletedFailurePublishesFailedStatus() {
    publisher.stageCompleted(
        1L, "pipe", 1, "test", false);

    ArgumentCaptor<StatusUpdateMessage> captor =
        ArgumentCaptor.forClass(StatusUpdateMessage.class);
    verify(rabbitTemplate).convertAndSend(
        anyString(), anyString(), captor.capture());

    assertEquals("FAILED",
        captor.getValue().getStatus());
  }

  // ── jobCreated ─────────────────────────────────────

  @Test
  void jobCreatedPublishesEntityTypeJobWithPendingStatus() {
    publisher.jobCreated(
        1L, "pipe", 1, "build", "compile", false);

    ArgumentCaptor<StatusUpdateMessage> captor =
        ArgumentCaptor.forClass(StatusUpdateMessage.class);
    verify(rabbitTemplate).convertAndSend(
        anyString(), anyString(), captor.capture());

    StatusUpdateMessage msg = captor.getValue();
    assertEquals("JOB", msg.getEntityType());
    assertEquals("build", msg.getStageName());
    assertEquals("compile", msg.getJobName());
    assertEquals("PENDING", msg.getStatus());
    assertNotNull(msg.getStartTime());
  }

  // ── jobStarted ─────────────────────────────────────

  @Test
  void jobStartedPublishesRunningStatus() {
    publisher.jobStarted(
        1L, "pipe", 1, "build", "compile");

    ArgumentCaptor<StatusUpdateMessage> captor =
        ArgumentCaptor.forClass(StatusUpdateMessage.class);
    verify(rabbitTemplate).convertAndSend(
        anyString(), anyString(), captor.capture());

    assertEquals("RUNNING",
        captor.getValue().getStatus());
  }

  // ── jobCompleted ───────────────────────────────────

  @Test
  void jobCompletedSuccessPublishesSuccessStatus() {
    publisher.jobCompleted(
        1L, "pipe", 1, "build", "compile", true, false);

    ArgumentCaptor<StatusUpdateMessage> captor =
        ArgumentCaptor.forClass(StatusUpdateMessage.class);
    verify(rabbitTemplate).convertAndSend(
        anyString(), anyString(), captor.capture());

    assertEquals("SUCCESS",
        captor.getValue().getStatus());
    assertNotNull(captor.getValue().getEndTime());
  }

  @Test
  void jobCompletedFailurePublishesFailedStatus() {
    publisher.jobCompleted(
        1L, "pipe", 1, "build", "compile", false, false);

    ArgumentCaptor<StatusUpdateMessage> captor =
        ArgumentCaptor.forClass(StatusUpdateMessage.class);
    verify(rabbitTemplate).convertAndSend(
        anyString(), anyString(), captor.capture());

    assertEquals("FAILED",
        captor.getValue().getStatus());
  }

  // ── All messages published to correct exchange/key ─

  @Test
  void allMessagesPublishedToStatusExchange() {
    publisher.pipelineRunning(1L, "p", 1);
    publisher.stageStarted(1L, "p", 1, "build", 0);
    publisher.jobCreated(
        1L, "p", 1, "build", "compile", false);

    verify(rabbitTemplate, times(3))
        .convertAndSend(anyString(), anyString(),
            any(StatusUpdateMessage.class));
  }
}

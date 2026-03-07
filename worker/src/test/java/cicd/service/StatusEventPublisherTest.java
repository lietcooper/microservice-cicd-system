package cicd.service;

import cicd.messaging.StatusEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class StatusEventPublisherTest {

  private RabbitTemplate rabbitTemplate;
  private StatusEventPublisher publisher;

  @BeforeEach
  void setUp() {
    rabbitTemplate = mock(RabbitTemplate.class);
    publisher = new StatusEventPublisher(rabbitTemplate);
  }

  // ── publishPipelineStarted ───────────────────────────────────────────────────

  @Test
  void publishPipelineStartedSetsEventTypeAndStatus() {
    publisher.publishPipelineStarted(1L, "default", 1);

    ArgumentCaptor<StatusEventMessage> captor =
        ArgumentCaptor.forClass(StatusEventMessage.class);
    verify(rabbitTemplate).convertAndSend(anyString(), anyString(), captor.capture());

    StatusEventMessage msg = captor.getValue();
    assertEquals("pipeline.started", msg.getEventType());
    assertEquals(1L, msg.getPipelineRunId());
    assertEquals("default", msg.getPipelineName());
    assertEquals(1, msg.getRunNo());
    assertEquals("RUNNING", msg.getStatus());
    assertNull(msg.getStageName());
    assertNull(msg.getJobName());
    assertNotNull(msg.getTimestamp());
    assertEquals("Pipeline started", msg.getMessage());
  }

  // ── publishPipelineCompleted ─────────────────────────────────────────────────

  @Test
  void publishPipelineCompletedSuccessSetsSucessStatus() {
    publisher.publishPipelineCompleted(2L, "pipe", 3, true);

    ArgumentCaptor<StatusEventMessage> captor =
        ArgumentCaptor.forClass(StatusEventMessage.class);
    verify(rabbitTemplate).convertAndSend(anyString(), anyString(), captor.capture());

    StatusEventMessage msg = captor.getValue();
    assertEquals("pipeline.completed", msg.getEventType());
    assertEquals("SUCCESS", msg.getStatus());
    assertTrue(msg.getMessage().contains("success"));
  }

  @Test
  void publishPipelineCompletedFailureSetsFailedStatus() {
    publisher.publishPipelineCompleted(2L, "pipe", 3, false);

    ArgumentCaptor<StatusEventMessage> captor =
        ArgumentCaptor.forClass(StatusEventMessage.class);
    verify(rabbitTemplate).convertAndSend(anyString(), anyString(), captor.capture());

    assertEquals("FAILED", captor.getValue().getStatus());
    assertTrue(captor.getValue().getMessage().contains("failed"));
  }

  // ── publishStageStarted ──────────────────────────────────────────────────────

  @Test
  void publishStageStartedSetsStageNameAndStatus() {
    publisher.publishStageStarted(1L, "pipe", 1, "build");

    ArgumentCaptor<StatusEventMessage> captor =
        ArgumentCaptor.forClass(StatusEventMessage.class);
    verify(rabbitTemplate).convertAndSend(anyString(), anyString(), captor.capture());

    StatusEventMessage msg = captor.getValue();
    assertEquals("stage.started", msg.getEventType());
    assertEquals("build", msg.getStageName());
    assertEquals("RUNNING", msg.getStatus());
    assertNull(msg.getJobName());
    assertTrue(msg.getMessage().contains("build"));
  }

  // ── publishStageCompleted ────────────────────────────────────────────────────

  @Test
  void publishStageCompletedSuccessSetsSuccessStatus() {
    publisher.publishStageCompleted(1L, "pipe", 1, "build", true);

    ArgumentCaptor<StatusEventMessage> captor =
        ArgumentCaptor.forClass(StatusEventMessage.class);
    verify(rabbitTemplate).convertAndSend(anyString(), anyString(), captor.capture());

    assertEquals("stage.completed", captor.getValue().getEventType());
    assertEquals("SUCCESS", captor.getValue().getStatus());
  }

  @Test
  void publishStageCompletedFailureSetsFailedStatus() {
    publisher.publishStageCompleted(1L, "pipe", 1, "test", false);

    ArgumentCaptor<StatusEventMessage> captor =
        ArgumentCaptor.forClass(StatusEventMessage.class);
    verify(rabbitTemplate).convertAndSend(anyString(), anyString(), captor.capture());

    assertEquals("FAILED", captor.getValue().getStatus());
  }

  // ── publishJobStarted ────────────────────────────────────────────────────────

  @Test
  void publishJobStartedSetsJobNameAndStageName() {
    publisher.publishJobStarted(1L, "pipe", 1, "build", "compile");

    ArgumentCaptor<StatusEventMessage> captor =
        ArgumentCaptor.forClass(StatusEventMessage.class);
    verify(rabbitTemplate).convertAndSend(anyString(), anyString(), captor.capture());

    StatusEventMessage msg = captor.getValue();
    assertEquals("job.started", msg.getEventType());
    assertEquals("build", msg.getStageName());
    assertEquals("compile", msg.getJobName());
    assertEquals("RUNNING", msg.getStatus());
    assertTrue(msg.getMessage().contains("compile"));
  }

  // ── publishJobCompleted ──────────────────────────────────────────────────────

  @Test
  void publishJobCompletedSuccessSetsSuccessStatus() {
    publisher.publishJobCompleted(1L, "pipe", 1, "build", "compile", true);

    ArgumentCaptor<StatusEventMessage> captor =
        ArgumentCaptor.forClass(StatusEventMessage.class);
    verify(rabbitTemplate).convertAndSend(anyString(), anyString(), captor.capture());

    assertEquals("job.completed", captor.getValue().getEventType());
    assertEquals("SUCCESS", captor.getValue().getStatus());
  }

  @Test
  void publishJobCompletedFailureSetsFailedStatus() {
    publisher.publishJobCompleted(1L, "pipe", 1, "build", "compile", false);

    ArgumentCaptor<StatusEventMessage> captor =
        ArgumentCaptor.forClass(StatusEventMessage.class);
    verify(rabbitTemplate).convertAndSend(anyString(), anyString(), captor.capture());

    assertEquals("FAILED", captor.getValue().getStatus());
  }

  // ── Timestamp always set ─────────────────────────────────────────────────────

  @Test
  void allEventMessagesHaveTimestamp() {
    publisher.publishPipelineStarted(1L, "p", 1);
    publisher.publishStageStarted(1L, "p", 1, "build");
    publisher.publishJobStarted(1L, "p", 1, "build", "job");

    ArgumentCaptor<StatusEventMessage> captor =
        ArgumentCaptor.forClass(StatusEventMessage.class);
    verify(rabbitTemplate, times(3))
        .convertAndSend(anyString(), anyString(), captor.capture());

    captor.getAllValues().forEach(msg ->
        assertNotNull(msg.getTimestamp(), "Timestamp should always be set"));
  }
}

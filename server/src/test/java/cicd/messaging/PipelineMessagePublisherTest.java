package cicd.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cicd.config.RabbitMqConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Tests for PipelineMessagePublisher.
 * Verifies that the publisher sends messages to the correct exchange/routing-key
 * and that message fields are populated correctly.
 */
class PipelineMessagePublisherTest {

  private RabbitTemplate rabbitTemplate;
  private PipelineMessagePublisher publisher;

  @BeforeEach
  void setUp() {
    rabbitTemplate = mock(RabbitTemplate.class);
    publisher = new PipelineMessagePublisher(rabbitTemplate);
  }

  @Test
  void publishPipelineExecuteSendsToCorrectExchange() {
    PipelineExecuteMessage msg = buildMessage("default", 1);

    publisher.publishPipelineExecute(msg);

    verify(rabbitTemplate).convertAndSend(
        eq(RabbitMqConfig.PIPELINE_EXCHANGE),
        eq(RabbitMqConfig.PIPELINE_EXECUTE_KEY),
        eq(msg));
  }

  @Test
  void publishPipelineExecuteSendsWithCorrectRoutingKey() {
    PipelineExecuteMessage msg = buildMessage("release", 3);

    publisher.publishPipelineExecute(msg);

    ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
    verify(rabbitTemplate).convertAndSend(
        eq(RabbitMqConfig.PIPELINE_EXCHANGE),
        eq(RabbitMqConfig.PIPELINE_EXECUTE_KEY),
        msgCaptor.capture());

    // Verify the correct routing key constant is used
    assertEquals("pipeline.execute", RabbitMqConfig.PIPELINE_EXECUTE_KEY);
    assertEquals("cicd.pipeline.direct", RabbitMqConfig.PIPELINE_EXCHANGE);
  }

  @Test
  void publishPipelineExecutePassesMessageUnmodified() {
    PipelineExecuteMessage msg = new PipelineExecuteMessage(
        42L, "my-pipeline", 7,
        "pipeline:\n  name: my-pipeline",
        "/workspace", "feature-x", "deadbeef");

    publisher.publishPipelineExecute(msg);

    ArgumentCaptor<PipelineExecuteMessage> captor =
        ArgumentCaptor.forClass(PipelineExecuteMessage.class);
    verify(rabbitTemplate).convertAndSend(
        eq(RabbitMqConfig.PIPELINE_EXCHANGE),
        eq(RabbitMqConfig.PIPELINE_EXECUTE_KEY),
        captor.capture());

    PipelineExecuteMessage sent = captor.getValue();
    assertEquals(42L, sent.getPipelineRunId());
    assertEquals("my-pipeline", sent.getPipelineName());
    assertEquals(7, sent.getRunNo());
    assertEquals("/workspace", sent.getRepoPath());
    assertEquals("feature-x", sent.getGitBranch());
    assertEquals("deadbeef", sent.getGitCommit());
  }

  @Test
  void publishPipelineExecuteWithNullFieldsDoesNotThrow() {
    PipelineExecuteMessage msg = new PipelineExecuteMessage();
    // All fields null by default

    publisher.publishPipelineExecute(msg);

    verify(rabbitTemplate).convertAndSend(
        eq(RabbitMqConfig.PIPELINE_EXCHANGE),
        eq(RabbitMqConfig.PIPELINE_EXECUTE_KEY),
        eq(msg));
  }

  @Test
  void publishPipelineExecuteCanBeCalledMultipleTimes() {
    PipelineExecuteMessage msg1 = buildMessage("pipeline-a", 1);
    PipelineExecuteMessage msg2 = buildMessage("pipeline-b", 2);

    publisher.publishPipelineExecute(msg1);
    publisher.publishPipelineExecute(msg2);

    // Verify rabbitTemplate was called twice
    ArgumentCaptor<PipelineExecuteMessage> captor =
        ArgumentCaptor.forClass(PipelineExecuteMessage.class);
    verify(rabbitTemplate, org.mockito.Mockito.times(2)).convertAndSend(
        eq(RabbitMqConfig.PIPELINE_EXCHANGE),
        eq(RabbitMqConfig.PIPELINE_EXECUTE_KEY),
        captor.capture());

    assertEquals("pipeline-a", captor.getAllValues().get(0).getPipelineName());
    assertEquals("pipeline-b", captor.getAllValues().get(1).getPipelineName());
  }

  @Test
  void publishPipelineExecuteWithMinimalMessage() {
    PipelineExecuteMessage msg = new PipelineExecuteMessage();
    msg.setPipelineRunId(1L);
    msg.setPipelineName("minimal");
    msg.setRunNo(1);
    msg.setRepoPath("/repo");

    publisher.publishPipelineExecute(msg);

    verify(rabbitTemplate).convertAndSend(
        eq(RabbitMqConfig.PIPELINE_EXCHANGE),
        eq(RabbitMqConfig.PIPELINE_EXECUTE_KEY),
        eq(msg));
  }

  // ── Helper ───────────────────────────────────────────────────────────────────

  private PipelineExecuteMessage buildMessage(String pipelineName, int runNo) {
    return new PipelineExecuteMessage(
        (long) runNo, pipelineName, runNo,
        "pipeline:\n  name: " + pipelineName,
        "/workspace", "main", "abc123");
  }
}

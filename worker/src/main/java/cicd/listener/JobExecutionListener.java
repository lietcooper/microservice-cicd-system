package cicd.listener;

import cicd.config.RabbitMqConfig;
import cicd.docker.LocalDockerRunner;
import cicd.executor.JobResult;
import cicd.messaging.JobExecuteMessage;
import cicd.messaging.JobResultMessage;
import cicd.observability.TraceContextHelper;
import cicd.service.StatusEventPublisher;
import cicd.service.StatusUpdatePublisher;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Listens for job execution messages, runs Docker containers,
 * and publishes results back.
 */
@Component
public class JobExecutionListener {

  private static final Logger log =
      LoggerFactory.getLogger(JobExecutionListener.class);

  private final LocalDockerRunner dockerRunner;
  private final RabbitTemplate rabbitTemplate;
  private final StatusEventPublisher eventPublisher;
  private final StatusUpdatePublisher statusPublisher;
  private final Tracer tracer;

  /** Creates a listener with the given dependencies. */
  public JobExecutionListener(RabbitTemplate rabbitTemplate,
      StatusEventPublisher eventPublisher,
      StatusUpdatePublisher statusPublisher,
      Tracer tracer) {
    this.dockerRunner = new LocalDockerRunner();
    this.rabbitTemplate = rabbitTemplate;
    this.eventPublisher = eventPublisher;
    this.statusPublisher = statusPublisher;
    this.tracer = tracer;
  }

  /** Backward-compatible constructor for tests (no-op tracer). */
  public JobExecutionListener(RabbitTemplate rabbitTemplate,
      StatusEventPublisher eventPublisher,
      StatusUpdatePublisher statusPublisher) {
    this(rabbitTemplate, eventPublisher, statusPublisher,
        io.opentelemetry.api.OpenTelemetry.noop().getTracer("test"));
  }

  /** Executes a job in Docker and publishes the result. */
  @RabbitListener(queues = RabbitMqConfig.JOB_EXECUTE_QUEUE)
  public void onJobExecute(JobExecuteMessage msg,
      Message amqpMessage) {
    MDC.put("pipeline", msg.getPipelineName());
    MDC.put("run_no", String.valueOf(msg.getRunNo()));
    MDC.put("stage", msg.getStageName());
    MDC.put("job", msg.getJobName());
    MDC.put("source", "system");

    // Extract parent context (stage span) from message headers
    Context parentCtx = TraceContextHelper.extractContext(
        amqpMessage.getMessageProperties().getHeaders());
    Span jobSpan = tracer.spanBuilder("job: " + msg.getJobName())
        .setParent(parentCtx)
        .setAttribute(AttributeKey.stringKey("pipeline"),
            msg.getPipelineName())
        .setAttribute(AttributeKey.stringKey("stage"), msg.getStageName())
        .setAttribute(AttributeKey.stringKey("job"), msg.getJobName())
        .setAttribute(AttributeKey.stringKey("image"), msg.getImage())
        .startSpan();

    try (Scope ignored = jobSpan.makeCurrent()) {
      log.info("Job Worker: executing '{}' [{}]",
          msg.getJobName(), msg.getImage());

      // Update job status to RUNNING via MQ
      statusPublisher.jobStarted(msg.getPipelineRunId(),
          msg.getPipelineName(), msg.getRunNo(),
          msg.getStageName(), msg.getJobName());

      eventPublisher.publishJobStarted(msg.getPipelineRunId(),
          msg.getPipelineName(), msg.getRunNo(),
          msg.getStageName(), msg.getJobName());

      // Execute in Docker (container logs streamed via LocalDockerRunner)
      JobResult result = dockerRunner.runJob(
          msg.getImage(), msg.getScripts(), msg.getWorkspacePath());

      // Update job status via MQ
      statusPublisher.jobCompleted(msg.getPipelineRunId(),
          msg.getPipelineName(), msg.getRunNo(),
          msg.getStageName(), msg.getJobName(), result.ok());

      eventPublisher.publishJobCompleted(msg.getPipelineRunId(),
          msg.getPipelineName(), msg.getRunNo(),
          msg.getStageName(), msg.getJobName(), result.ok());

      // Publish result back to orchestrator
      JobResultMessage resultMsg = new JobResultMessage();
      resultMsg.setPipelineRunId(msg.getPipelineRunId());
      resultMsg.setCorrelationId(msg.getCorrelationId());
      resultMsg.setJobName(msg.getJobName());
      resultMsg.setStageName(msg.getStageName());
      resultMsg.setPipelineName(msg.getPipelineName());
      resultMsg.setSuccess(result.ok());
      resultMsg.setExitCode(result.exitCode());
      resultMsg.setOutput(result.output());
      resultMsg.setAllowFailure(msg.isAllowFailure());

      rabbitTemplate.convertAndSend(
          RabbitMqConfig.JOB_RESULTS_EXCHANGE,
          RabbitMqConfig.JOB_RESULT_KEY,
          resultMsg);

      if (result.ok()) {
        log.info("Job '{}' passed", msg.getJobName());
      } else {
        jobSpan.setStatus(StatusCode.ERROR, "job failed");
        log.error("Job '{}' failed", msg.getJobName());
      }
    } catch (Exception ex) {
      jobSpan.setStatus(StatusCode.ERROR, ex.getMessage());
      jobSpan.recordException(ex);
      throw ex;
    } finally {
      jobSpan.end();
      MDC.clear();
    }
  }

  /** Backward-compatible overload for tests (no AMQP headers). */
  public void onJobExecute(JobExecuteMessage msg) {
    onJobExecute(msg, new Message(new byte[0]));
  }
}

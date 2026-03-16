package cicd.service;

import cicd.config.RabbitMqConfig;
import cicd.messaging.StatusUpdateMessage;
import java.time.OffsetDateTime;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes status update messages to the server via RabbitMQ.
 * The server is the sole writer to the database.
 */
@Component
public class StatusUpdatePublisher {

  private final RabbitTemplate rabbitTemplate;

  /** Creates a publisher with the given template. */
  public StatusUpdatePublisher(
      RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  // --- Pipeline-level updates ---

  /** Publishes a pipeline-running status update. */
  public void pipelineRunning(Long pipelineRunId,
      String pipelineName, int runNo) {
    StatusUpdateMessage msg =
        buildPipeline(pipelineRunId, pipelineName, runNo);
    msg.setStatus("RUNNING");
    msg.setStartTime(OffsetDateTime.now());
    publish(msg);
  }

  /** Publishes a pipeline-failed status update. */
  public void pipelineFailed(Long pipelineRunId,
      String pipelineName, int runNo) {
    StatusUpdateMessage msg =
        buildPipeline(pipelineRunId, pipelineName, runNo);
    msg.setStatus("FAILED");
    msg.setEndTime(OffsetDateTime.now());
    publish(msg);
  }

  /** Publishes a pipeline-completed status update. */
  public void pipelineCompleted(Long pipelineRunId,
      String pipelineName, int runNo, boolean success) {
    StatusUpdateMessage msg =
        buildPipeline(pipelineRunId, pipelineName, runNo);
    msg.setStatus(success ? "SUCCESS" : "FAILED");
    msg.setEndTime(OffsetDateTime.now());
    publish(msg);
  }

  // --- Stage-level updates ---

  /** Publishes a stage-started status update. */
  public void stageStarted(Long pipelineRunId,
      String pipelineName, int runNo, String stageName,
      int stageOrder) {
    StatusUpdateMessage msg = buildStage(
        pipelineRunId, pipelineName, runNo, stageName);
    msg.setStageOrder(stageOrder);
    msg.setStatus("RUNNING");
    msg.setStartTime(OffsetDateTime.now());
    publish(msg);
  }

  /** Publishes a stage-completed status update. */
  public void stageCompleted(Long pipelineRunId,
      String pipelineName, int runNo, String stageName,
      boolean success) {
    StatusUpdateMessage msg = buildStage(
        pipelineRunId, pipelineName, runNo, stageName);
    msg.setStatus(success ? "SUCCESS" : "FAILED");
    msg.setEndTime(OffsetDateTime.now());
    publish(msg);
  }

  // --- Job-level updates ---

  /** Publishes a job-created status update. */
  public void jobCreated(Long pipelineRunId,
      String pipelineName, int runNo, String stageName,
      String jobName, boolean allowFailure) {
    StatusUpdateMessage msg = buildJob(
        pipelineRunId, pipelineName, runNo,
        stageName, jobName);
    msg.setStatus("PENDING");
    msg.setStartTime(OffsetDateTime.now());
    msg.setAllowFailure(allowFailure);
    publish(msg);
  }

  /** Publishes a job-started status update. */
  public void jobStarted(Long pipelineRunId,
      String pipelineName, int runNo, String stageName,
      String jobName) {
    StatusUpdateMessage msg = buildJob(
        pipelineRunId, pipelineName, runNo,
        stageName, jobName);
    msg.setStatus("RUNNING");
    msg.setStartTime(OffsetDateTime.now());
    publish(msg);
  }

  /** Publishes a job-completed status update. */
  public void jobCompleted(Long pipelineRunId,
      String pipelineName, int runNo, String stageName,
      String jobName, boolean success) {
    StatusUpdateMessage msg = buildJob(
        pipelineRunId, pipelineName, runNo,
        stageName, jobName);
    msg.setStatus(success ? "SUCCESS" : "FAILED");
    msg.setEndTime(OffsetDateTime.now());
    publish(msg);
  }

  // --- Helpers ---

  private StatusUpdateMessage buildPipeline(
      Long pipelineRunId, String pipelineName, int runNo) {
    StatusUpdateMessage msg = new StatusUpdateMessage();
    msg.setEntityType("PIPELINE");
    msg.setPipelineRunId(pipelineRunId);
    msg.setPipelineName(pipelineName);
    msg.setRunNo(runNo);
    return msg;
  }

  private StatusUpdateMessage buildStage(
      Long pipelineRunId, String pipelineName,
      int runNo, String stageName) {
    StatusUpdateMessage msg = new StatusUpdateMessage();
    msg.setEntityType("STAGE");
    msg.setPipelineRunId(pipelineRunId);
    msg.setPipelineName(pipelineName);
    msg.setRunNo(runNo);
    msg.setStageName(stageName);
    return msg;
  }

  private StatusUpdateMessage buildJob(
      Long pipelineRunId, String pipelineName,
      int runNo, String stageName, String jobName) {
    StatusUpdateMessage msg = new StatusUpdateMessage();
    msg.setEntityType("JOB");
    msg.setPipelineRunId(pipelineRunId);
    msg.setPipelineName(pipelineName);
    msg.setRunNo(runNo);
    msg.setStageName(stageName);
    msg.setJobName(jobName);
    return msg;
  }

  private void publish(StatusUpdateMessage msg) {
    rabbitTemplate.convertAndSend(
        RabbitMqConfig.STATUS_EXCHANGE,
        RabbitMqConfig.STATUS_UPDATE_KEY,
        msg);
  }
}

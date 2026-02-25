package cicd.service;

import cicd.config.RabbitMqConfig;
import cicd.messaging.StatusEventMessage;
import java.time.OffsetDateTime;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes status change events to the topic exchange.
 */
@Component("workerStatusEventPublisher")
public class StatusEventPublisher {

  private final RabbitTemplate rabbitTemplate;

  public StatusEventPublisher(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  public void publishPipelineStarted(Long runId, String name, int runNo) {
    publish("pipeline.started", runId, name, runNo, null, null, "RUNNING",
        "Pipeline started");
  }

  public void publishPipelineCompleted(Long runId, String name, int runNo,
      boolean success) {
    String status = success ? "SUCCESS" : "FAILED";
    publish("pipeline.completed", runId, name, runNo, null, null, status,
        "Pipeline " + status.toLowerCase());
  }

  public void publishStageStarted(Long runId, String pipelineName, int runNo,
      String stageName) {
    publish("stage.started", runId, pipelineName, runNo, stageName, null,
        "RUNNING", "Stage '" + stageName + "' started");
  }

  public void publishStageCompleted(Long runId, String pipelineName, int runNo,
      String stageName, boolean success) {
    String status = success ? "SUCCESS" : "FAILED";
    publish("stage.completed", runId, pipelineName, runNo, stageName, null,
        status, "Stage '" + stageName + "' " + status.toLowerCase());
  }

  public void publishJobStarted(Long runId, String pipelineName, int runNo,
      String stageName, String jobName) {
    publish("job.started", runId, pipelineName, runNo, stageName, jobName,
        "RUNNING", "Job '" + jobName + "' started");
  }

  public void publishJobCompleted(Long runId, String pipelineName, int runNo,
      String stageName, String jobName, boolean success) {
    String status = success ? "SUCCESS" : "FAILED";
    publish("job.completed", runId, pipelineName, runNo, stageName, jobName,
        status, "Job '" + jobName + "' " + status.toLowerCase());
  }

  private void publish(String eventType, Long runId, String pipelineName,
      int runNo, String stageName, String jobName,
      String status, String message) {
    StatusEventMessage event = new StatusEventMessage();
    event.setEventType(eventType);
    event.setPipelineRunId(runId);
    event.setPipelineName(pipelineName);
    event.setRunNo(runNo);
    event.setStageName(stageName);
    event.setJobName(jobName);
    event.setStatus(status);
    event.setTimestamp(OffsetDateTime.now());
    event.setMessage(message);

    rabbitTemplate.convertAndSend(
        RabbitMqConfig.EVENTS_EXCHANGE, eventType, event);
  }
}

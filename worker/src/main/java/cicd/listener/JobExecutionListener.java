package cicd.listener;

import cicd.config.RabbitMqConfig;
import cicd.docker.LocalDockerRunner;
import cicd.executor.JobResult;
import cicd.messaging.JobExecuteMessage;
import cicd.messaging.JobResultMessage;
import cicd.service.StatusEventPublisher;
import cicd.service.StatusUpdatePublisher;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Listens for job execution messages, runs Docker containers,
 * and publishes results back.
 */
@Component
public class JobExecutionListener {

  private final LocalDockerRunner dockerRunner;
  private final RabbitTemplate rabbitTemplate;
  private final StatusEventPublisher eventPublisher;
  private final StatusUpdatePublisher statusPublisher;

  /** Creates a listener with the given dependencies. */
  public JobExecutionListener(RabbitTemplate rabbitTemplate,
      StatusEventPublisher eventPublisher,
      StatusUpdatePublisher statusPublisher) {
    this.dockerRunner = new LocalDockerRunner();
    this.rabbitTemplate = rabbitTemplate;
    this.eventPublisher = eventPublisher;
    this.statusPublisher = statusPublisher;
  }

  /** Executes a job in Docker and publishes the result. */
  @RabbitListener(queues = RabbitMqConfig.JOB_EXECUTE_QUEUE)
  public void onJobExecute(JobExecuteMessage msg) {
    System.out.println("  > Job Worker: executing '" + msg.getJobName()
        + "' [" + msg.getImage() + "]");

    // Update job status to RUNNING via MQ
    statusPublisher.jobStarted(msg.getPipelineRunId(),
        msg.getPipelineName(), msg.getRunNo(),
        msg.getStageName(), msg.getJobName());

    eventPublisher.publishJobStarted(msg.getPipelineRunId(),
        msg.getPipelineName(), msg.getRunNo(),
        msg.getStageName(), msg.getJobName());

    // Execute in Docker
    JobResult result = dockerRunner.runJob(
        msg.getImage(), msg.getScripts(), msg.getWorkspacePath());

    if (result.output() != null && !result.output().isBlank()) {
      result.output().lines().forEach(l -> System.out.println("    " + l));
    }

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

    rabbitTemplate.convertAndSend(
        RabbitMqConfig.JOB_RESULTS_EXCHANGE,
        RabbitMqConfig.JOB_RESULT_KEY,
        resultMsg);

    System.out.println("  " + (result.ok() ? "v" : "x") + " Job '"
        + msg.getJobName() + "' " + (result.ok() ? "passed" : "failed"));
  }
}

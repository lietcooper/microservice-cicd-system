package cicd.listener;

import cicd.config.RabbitMqConfig;
import cicd.docker.LocalDockerRunner;
import cicd.executor.JobResult;
import cicd.messaging.JobExecuteMessage;
import cicd.messaging.JobResultMessage;
import cicd.persistence.entity.JobRunEntity;
import cicd.persistence.entity.RunStatus;
import cicd.persistence.repository.JobRunRepository;
import cicd.service.StatusEventPublisher;
import java.time.OffsetDateTime;
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
  private final JobRunRepository jobRunRepo;
  private final StatusEventPublisher eventPublisher;

  public JobExecutionListener(RabbitTemplate rabbitTemplate,
      JobRunRepository jobRunRepo, StatusEventPublisher eventPublisher) {
    this.dockerRunner = new LocalDockerRunner();
    this.rabbitTemplate = rabbitTemplate;
    this.jobRunRepo = jobRunRepo;
    this.eventPublisher = eventPublisher;
  }

  @RabbitListener(queues = RabbitMqConfig.JOB_EXECUTE_QUEUE)
  public void onJobExecute(JobExecuteMessage msg) {
    System.out.println("  > Job Worker: executing '" + msg.getJobName()
        + "' [" + msg.getImage() + "]");

    // Update job status to RUNNING
    JobRunEntity jobRun = jobRunRepo.findById(msg.getJobRunId()).orElse(null);
    if (jobRun != null) {
      jobRun.setStatus(RunStatus.RUNNING);
      jobRun.setStartTime(OffsetDateTime.now());
      jobRunRepo.save(jobRun);
      jobRunRepo.flush();
    }

    eventPublisher.publishJobStarted(msg.getPipelineRunId(),
        msg.getPipelineName(), 0, msg.getStageName(), msg.getJobName());

    // Execute in Docker
    JobResult result = dockerRunner.runJob(
        msg.getImage(), msg.getScripts(), msg.getRepoPath());

    if (result.output != null && !result.output.isBlank()) {
      result.output.lines().forEach(l -> System.out.println("    " + l));
    }

    // Update job in DB
    if (jobRun != null) {
      jobRun.setEndTime(OffsetDateTime.now());
      jobRun.setStatus(result.ok() ? RunStatus.SUCCESS : RunStatus.FAILED);
      jobRunRepo.save(jobRun);
      jobRunRepo.flush();
    }

    eventPublisher.publishJobCompleted(msg.getPipelineRunId(),
        msg.getPipelineName(), 0, msg.getStageName(), msg.getJobName(),
        result.ok());

    // Publish result back to orchestrator
    JobResultMessage resultMsg = new JobResultMessage();
    resultMsg.setPipelineRunId(msg.getPipelineRunId());
    resultMsg.setStageRunId(msg.getStageRunId());
    resultMsg.setJobRunId(msg.getJobRunId());
    resultMsg.setCorrelationId(msg.getCorrelationId());
    resultMsg.setJobName(msg.getJobName());
    resultMsg.setStageName(msg.getStageName());
    resultMsg.setPipelineName(msg.getPipelineName());
    resultMsg.setSuccess(result.ok());
    resultMsg.setExitCode(result.exitCode);
    resultMsg.setOutput(result.output);

    rabbitTemplate.convertAndSend(
        RabbitMqConfig.JOB_RESULTS_EXCHANGE,
        RabbitMqConfig.JOB_RESULT_KEY,
        resultMsg);

    System.out.println("  " + (result.ok() ? "v" : "x") + " Job '"
        + msg.getJobName() + "' " + (result.ok() ? "passed" : "failed"));
  }
}

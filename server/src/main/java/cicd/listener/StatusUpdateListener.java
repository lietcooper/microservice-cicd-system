package cicd.listener;

import cicd.config.RabbitMqConfig;
import cicd.messaging.StatusUpdateMessage;
import cicd.observability.CicdMetrics;
import cicd.persistence.entity.JobRunEntity;
import cicd.persistence.entity.PipelineRunEntity;
import cicd.persistence.entity.RunStatus;
import cicd.persistence.entity.StageRunEntity;
import cicd.persistence.repository.JobRunRepository;
import cicd.persistence.repository.PipelineRunRepository;
import cicd.persistence.repository.StageRunRepository;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Listens for status update messages from the Worker and persists
 * them to the database. Server is the sole writer to the DB.
 */
@Component
public class StatusUpdateListener {

  private static final Logger log =
      LoggerFactory.getLogger(StatusUpdateListener.class);

  private final PipelineRunRepository pipelineRunRepo;
  private final StageRunRepository stageRunRepo;
  private final JobRunRepository jobRunRepo;
  private final CicdMetrics metrics;

  /** Creates a listener with the required repositories and optional metrics. */
  public StatusUpdateListener(PipelineRunRepository pipelineRunRepo,
      StageRunRepository stageRunRepo, JobRunRepository jobRunRepo,
      ObjectProvider<CicdMetrics> metricsProvider) {
    this.pipelineRunRepo = pipelineRunRepo;
    this.stageRunRepo = stageRunRepo;
    this.jobRunRepo = jobRunRepo;
    this.metrics = metricsProvider.getIfAvailable();
  }

  /** Handles incoming status update messages. */
  @RabbitListener(queues = RabbitMqConfig.STATUS_UPDATE_QUEUE,
      concurrency = "1")
  public void onStatusUpdate(StatusUpdateMessage msg) {
    switch (msg.getEntityType()) {
      case "PIPELINE" -> handlePipeline(msg);
      case "STAGE" -> handleStage(msg);
      case "JOB" -> handleJob(msg);
      default -> log.error("Unknown entity type: {}",
          msg.getEntityType());
    }
  }

  private void handlePipeline(StatusUpdateMessage msg) {
    PipelineRunEntity run = pipelineRunRepo
        .findById(msg.getPipelineRunId()).orElse(null);
    if (run == null) {
      log.error("Pipeline run not found: {}", msg.getPipelineRunId());
      return;
    }

    run.setStatus(RunStatus.valueOf(msg.getStatus()));
    if (msg.getStartTime() != null) {
      run.setStartTime(msg.getStartTime());
    }
    if (msg.getEndTime() != null) {
      run.setEndTime(msg.getEndTime());
    }
    if (msg.getTraceId() != null) {
      run.setTraceId(msg.getTraceId());
    }
    pipelineRunRepo.save(run);
    pipelineRunRepo.flush();

    if (metrics != null && isTerminal(msg.getStatus())
        && run.getStartTime() != null && run.getEndTime() != null) {
      long durationMs = Duration.between(
          run.getStartTime(), run.getEndTime()).toMillis();
      metrics.recordPipelineCompleted(
          msg.getPipelineName(), msg.getStatus(), durationMs);
    }
  }

  private void handleStage(StatusUpdateMessage msg) {
    PipelineRunEntity pipelineRun = pipelineRunRepo
        .findById(msg.getPipelineRunId()).orElse(null);
    if (pipelineRun == null) {
      log.error("Pipeline run not found for stage update: {}",
          msg.getPipelineRunId());
      return;
    }

    StageRunEntity stageRun = stageRunRepo
        .findByPipelineRunIdAndStageName(
            msg.getPipelineRunId(), msg.getStageName())
        .orElse(null);

    if (stageRun == null) {
      stageRun = new StageRunEntity();
      stageRun.setPipelineRun(pipelineRun);
      stageRun.setStageName(msg.getStageName());
      stageRun.setStageOrder(
          msg.getStageOrder() != null ? msg.getStageOrder() : 0);
    }

    stageRun.setStatus(RunStatus.valueOf(msg.getStatus()));
    if (msg.getStartTime() != null) {
      stageRun.setStartTime(msg.getStartTime());
    }
    if (msg.getEndTime() != null) {
      stageRun.setEndTime(msg.getEndTime());
    }
    stageRunRepo.save(stageRun);
    stageRunRepo.flush();

    if (metrics != null && isTerminal(msg.getStatus())
        && stageRun.getStartTime() != null
        && stageRun.getEndTime() != null) {
      long durationMs = Duration.between(
          stageRun.getStartTime(), stageRun.getEndTime()).toMillis();
      metrics.recordStageCompleted(
          msg.getPipelineName(), msg.getStageName(), durationMs);
    }
  }

  private void handleJob(StatusUpdateMessage msg) {
    StageRunEntity stageRun = stageRunRepo
        .findByPipelineRunIdAndStageName(
            msg.getPipelineRunId(), msg.getStageName())
        .orElse(null);

    if (stageRun == null) {
      log.error("Stage run not found for job update: pipeline={} stage={}",
          msg.getPipelineRunId(), msg.getStageName());
      return;
    }

    JobRunEntity jobRun = jobRunRepo
        .findByStageRunIdAndJobName(stageRun.getId(), msg.getJobName())
        .orElse(null);

    if (jobRun == null) {
      jobRun = new JobRunEntity();
      jobRun.setStageRun(stageRun);
      jobRun.setJobName(msg.getJobName());
    }

    jobRun.setStatus(RunStatus.valueOf(msg.getStatus()));
    if (msg.getStartTime() != null) {
      jobRun.setStartTime(msg.getStartTime());
    }
    if (msg.getEndTime() != null) {
      jobRun.setEndTime(msg.getEndTime());
    }
    if (msg.getAllowFailure() != null) {
      jobRun.setAllowFailure(msg.getAllowFailure());
    }
    jobRunRepo.save(jobRun);
    jobRunRepo.flush();

    if (metrics != null && isTerminal(msg.getStatus())
        && jobRun.getStartTime() != null
        && jobRun.getEndTime() != null) {
      long durationMs = Duration.between(
          jobRun.getStartTime(), jobRun.getEndTime()).toMillis();
      metrics.recordJobCompleted(msg.getPipelineName(), msg.getStageName(),
          msg.getJobName(), msg.getStatus(), durationMs);
    }
  }

  private boolean isTerminal(String status) {
    return "SUCCESS".equals(status) || "FAILED".equals(status);
  }
}

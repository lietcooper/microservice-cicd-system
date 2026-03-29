package cicd.listener;

import cicd.config.RabbitMqConfig;
import cicd.messaging.StatusUpdateMessage;
import cicd.persistence.entity.JobRunEntity;
import cicd.persistence.entity.PipelineRunEntity;
import cicd.persistence.entity.RunStatus;
import cicd.persistence.entity.StageRunEntity;
import cicd.persistence.repository.JobRunRepository;
import cicd.persistence.repository.PipelineRunRepository;
import cicd.persistence.repository.StageRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
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

  /** Creates a listener with the required repositories. */
  public StatusUpdateListener(PipelineRunRepository pipelineRunRepo,
      StageRunRepository stageRunRepo, JobRunRepository jobRunRepo) {
    this.pipelineRunRepo = pipelineRunRepo;
    this.stageRunRepo = stageRunRepo;
    this.jobRunRepo = jobRunRepo;
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
    pipelineRunRepo.save(run);
    pipelineRunRepo.flush();
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
  }
}

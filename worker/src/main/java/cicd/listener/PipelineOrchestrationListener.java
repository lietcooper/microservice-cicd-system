package cicd.listener;

import cicd.config.RabbitMqConfig;
import cicd.executor.ExecutionPlanner;
import cicd.messaging.JobExecuteMessage;
import cicd.messaging.PipelineExecuteMessage;
import cicd.model.Job;
import cicd.model.Pipeline;
import cicd.parser.YamlParser;
import cicd.persistence.entity.JobRunEntity;
import cicd.persistence.entity.PipelineRunEntity;
import cicd.persistence.entity.RunStatus;
import cicd.persistence.entity.StageRunEntity;
import cicd.persistence.repository.JobRunRepository;
import cicd.persistence.repository.PipelineRunRepository;
import cicd.persistence.repository.StageRunRepository;
import cicd.service.StageCoordinatorService;
import cicd.service.StatusEventPublisher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates pipeline execution: receives pipeline messages,
 * breaks them into stages and waves, dispatches jobs, and
 * waits for results via the StageCoordinatorService.
 */
@Component
public class PipelineOrchestrationListener {

  private final PipelineRunRepository pipelineRunRepo;
  private final StageRunRepository stageRunRepo;
  private final JobRunRepository jobRunRepo;
  private final RabbitTemplate rabbitTemplate;
  private final StageCoordinatorService coordinator;
  private final StatusEventPublisher eventPublisher;

  public PipelineOrchestrationListener(
      PipelineRunRepository pipelineRunRepo,
      StageRunRepository stageRunRepo,
      JobRunRepository jobRunRepo,
      RabbitTemplate rabbitTemplate,
      StageCoordinatorService coordinator,
      StatusEventPublisher eventPublisher) {
    this.pipelineRunRepo = pipelineRunRepo;
    this.stageRunRepo = stageRunRepo;
    this.jobRunRepo = jobRunRepo;
    this.rabbitTemplate = rabbitTemplate;
    this.coordinator = coordinator;
    this.eventPublisher = eventPublisher;
  }

  @RabbitListener(queues = RabbitMqConfig.PIPELINE_EXECUTE_QUEUE,
      concurrency = "1")
  public void onPipelineExecute(PipelineExecuteMessage msg) {
    System.out.println("=== Orchestrator: running pipeline '"
        + msg.getPipelineName() + "' run #" + msg.getRunNo() + " ===");

    PipelineRunEntity pipelineRun = pipelineRunRepo
        .findById(msg.getPipelineRunId()).orElse(null);
    if (pipelineRun == null) {
      System.err.println("Pipeline run not found: " + msg.getPipelineRunId());
      return;
    }

    // Update to RUNNING
    pipelineRun.setStatus(RunStatus.RUNNING);
    pipelineRun.setStartTime(OffsetDateTime.now());
    pipelineRunRepo.save(pipelineRun);
    pipelineRunRepo.flush();

    eventPublisher.publishPipelineStarted(
        pipelineRun.getId(), msg.getPipelineName(), msg.getRunNo());

    // Parse pipeline from YAML
    Pipeline pipeline = parsePipelineYaml(msg.getPipelineYaml());
    if (pipeline == null) {
      failPipeline(pipelineRun);
      return;
    }

    // Compute wave-based execution plan
    ExecutionPlanner planner = new ExecutionPlanner(pipeline);
    List<ExecutionPlanner.WaveStageExecution> plan = planner.computeWaveOrder();

    boolean pipelineFailed = false;
    int stageOrder = 0;

    for (ExecutionPlanner.WaveStageExecution stage : plan) {
      System.out.println("\n--- Stage: " + stage.getStageName() + " ---");

      // Create stage record
      StageRunEntity stageRun = new StageRunEntity();
      stageRun.setPipelineRun(pipelineRun);
      stageRun.setStageName(stage.getStageName());
      stageRun.setStageOrder(stageOrder++);
      stageRun.setStatus(RunStatus.RUNNING);
      stageRun.setStartTime(OffsetDateTime.now());
      stageRun = stageRunRepo.save(stageRun);
      stageRunRepo.flush();

      eventPublisher.publishStageStarted(pipelineRun.getId(),
          msg.getPipelineName(), msg.getRunNo(), stage.getStageName());

      boolean stageFailed = false;

      // Process each wave in the stage
      for (List<Job> wave : stage.getWaves()) {
        String correlationId = UUID.randomUUID().toString();
        coordinator.registerWave(correlationId, wave.size());

        // Fan-out: publish job messages for all jobs in this wave
        for (Job job : wave) {
          // Create job record in DB
          JobRunEntity jobRun = new JobRunEntity();
          jobRun.setStageRun(stageRun);
          jobRun.setJobName(job.name);
          jobRun.setStatus(RunStatus.PENDING);
          jobRun.setStartTime(OffsetDateTime.now());
          jobRun = jobRunRepo.save(jobRun);
          jobRunRepo.flush();

          // Build and publish job execution message
          JobExecuteMessage jobMsg = new JobExecuteMessage();
          jobMsg.setPipelineRunId(msg.getPipelineRunId());
          jobMsg.setStageRunId(stageRun.getId());
          jobMsg.setJobRunId(jobRun.getId());
          jobMsg.setCorrelationId(correlationId);
          jobMsg.setJobName(job.name);
          jobMsg.setStageName(stage.getStageName());
          jobMsg.setPipelineName(msg.getPipelineName());
          jobMsg.setImage(job.image);
          jobMsg.setScripts(job.script);
          jobMsg.setRepoPath(msg.getRepoPath());
          jobMsg.setTotalJobsInWave(wave.size());

          rabbitTemplate.convertAndSend(
              RabbitMqConfig.JOB_EXCHANGE,
              RabbitMqConfig.JOB_EXECUTE_KEY,
              jobMsg);

          System.out.println("  > Dispatched job: " + job.name
              + " [" + job.image + "] wave=" + correlationId);
        }

        // Fan-in: wait for all jobs in this wave to complete
        StageCoordinatorService.WaveTracker tracker =
            coordinator.awaitWave(correlationId);
        coordinator.removeWave(correlationId);

        if (tracker == null || !tracker.allSucceeded()) {
          stageFailed = true;
          pipelineFailed = true;
          if (tracker != null && tracker.isTimedOut()) {
            System.err.println("  x Wave timed out in stage '"
                + stage.getStageName() + "'");
          }
          break; // Stop processing waves in this stage
        }
      }

      // Update stage record
      stageRun.setEndTime(OffsetDateTime.now());
      stageRun.setStatus(stageFailed ? RunStatus.FAILED : RunStatus.SUCCESS);
      stageRunRepo.save(stageRun);
      stageRunRepo.flush();

      eventPublisher.publishStageCompleted(pipelineRun.getId(),
          msg.getPipelineName(), msg.getRunNo(), stage.getStageName(),
          !stageFailed);

      if (pipelineFailed) {
        System.err.println("=== Pipeline FAILED ===");
        break;
      }
    }

    // Update pipeline record
    pipelineRun.setEndTime(OffsetDateTime.now());
    pipelineRun.setStatus(pipelineFailed ? RunStatus.FAILED : RunStatus.SUCCESS);
    pipelineRunRepo.save(pipelineRun);
    pipelineRunRepo.flush();

    eventPublisher.publishPipelineCompleted(pipelineRun.getId(),
        msg.getPipelineName(), msg.getRunNo(), !pipelineFailed);

    if (!pipelineFailed) {
      System.out.println("\n=== Pipeline PASSED ===");
    }
  }

  private Pipeline parsePipelineYaml(String yamlContent) {
    try {
      Path tempFile = Files.createTempFile("pipeline-worker-", ".yaml");
      Files.writeString(tempFile, yamlContent);
      YamlParser parser = new YamlParser(tempFile.toString());
      Pipeline pipeline = parser.parse();
      Files.deleteIfExists(tempFile);

      if (!parser.getErrors().isEmpty()) {
        System.err.println("YAML parse errors: "
            + String.join("\n", parser.getErrors()));
        return null;
      }
      return pipeline;
    } catch (IOException e) {
      System.err.println("Failed to parse pipeline YAML: " + e.getMessage());
      return null;
    }
  }

  private void failPipeline(PipelineRunEntity pipelineRun) {
    pipelineRun.setStatus(RunStatus.FAILED);
    pipelineRun.setEndTime(OffsetDateTime.now());
    pipelineRunRepo.save(pipelineRun);
    pipelineRunRepo.flush();
  }
}

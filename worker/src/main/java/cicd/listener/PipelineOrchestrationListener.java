package cicd.listener;

import cicd.config.RabbitMqConfig;
import cicd.executor.ExecutionPlanner;
import cicd.messaging.JobExecuteMessage;
import cicd.messaging.PipelineExecuteMessage;
import cicd.model.Job;
import cicd.model.Pipeline;
import cicd.parser.YamlParser;
import cicd.service.StageCoordinatorService;
import cicd.service.StatusEventPublisher;
import cicd.service.StatusUpdatePublisher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Orchestrates pipeline execution: receives pipeline messages,
 * breaks them into stages and waves, dispatches jobs, and
 * waits for results via the StageCoordinatorService.
 */
@Component
public class PipelineOrchestrationListener {

  private final RabbitTemplate rabbitTemplate;
  private final StageCoordinatorService coordinator;
  private final StatusEventPublisher eventPublisher;
  private final StatusUpdatePublisher statusPublisher;

  public PipelineOrchestrationListener(
      RabbitTemplate rabbitTemplate,
      StageCoordinatorService coordinator,
      StatusEventPublisher eventPublisher,
      StatusUpdatePublisher statusPublisher) {
    this.rabbitTemplate = rabbitTemplate;
    this.coordinator = coordinator;
    this.eventPublisher = eventPublisher;
    this.statusPublisher = statusPublisher;
  }

  @RabbitListener(queues = RabbitMqConfig.PIPELINE_EXECUTE_QUEUE,
      concurrency = "1")
  public void onPipelineExecute(PipelineExecuteMessage msg) {
    System.out.println("=== Orchestrator: running pipeline '"
        + msg.getPipelineName() + "' run #" + msg.getRunNo() + " ===");

    // Update to RUNNING via MQ
    statusPublisher.pipelineRunning(
        msg.getPipelineRunId(), msg.getPipelineName(), msg.getRunNo());

    eventPublisher.publishPipelineStarted(
        msg.getPipelineRunId(), msg.getPipelineName(), msg.getRunNo());

    // Parse pipeline from YAML
    Pipeline pipeline = parsePipelineYaml(msg.getPipelineYaml());
    if (pipeline == null) {
      statusPublisher.pipelineFailed(
          msg.getPipelineRunId(), msg.getPipelineName(), msg.getRunNo());
      return;
    }

    // Compute wave-based execution plan
    ExecutionPlanner planner = new ExecutionPlanner(pipeline);
    List<ExecutionPlanner.WaveStageExecution> plan = planner.computeWaveOrder();

    boolean pipelineFailed = false;
    int stageOrder = 0;

    for (ExecutionPlanner.WaveStageExecution stage : plan) {
      System.out.println("\n--- Stage: " + stage.getStageName() + " ---");

      int currentStageOrder = stageOrder++;

      // Create stage record via MQ
      statusPublisher.stageStarted(msg.getPipelineRunId(),
          msg.getPipelineName(), msg.getRunNo(),
          stage.getStageName(), currentStageOrder);

      eventPublisher.publishStageStarted(msg.getPipelineRunId(),
          msg.getPipelineName(), msg.getRunNo(), stage.getStageName());

      boolean stageFailed = false;

      // Process each wave in the stage
      for (List<Job> wave : stage.getWaves()) {
        String correlationId = UUID.randomUUID().toString();
        coordinator.registerWave(correlationId, wave.size());

        // Fan-out: publish job messages for all jobs in this wave
        for (Job job : wave) {
          // Create job record via MQ
          statusPublisher.jobCreated(msg.getPipelineRunId(),
              msg.getPipelineName(), msg.getRunNo(),
              stage.getStageName(), job.name);

          // Build and publish job execution message
          JobExecuteMessage jobMsg = new JobExecuteMessage();
          jobMsg.setPipelineRunId(msg.getPipelineRunId());
          jobMsg.setCorrelationId(correlationId);
          jobMsg.setJobName(job.name);
          jobMsg.setStageName(stage.getStageName());
          jobMsg.setPipelineName(msg.getPipelineName());
          jobMsg.setImage(job.image);
          jobMsg.setScripts(job.script);
          jobMsg.setRepoPath(msg.getRepoPath());
          jobMsg.setTotalJobsInWave(wave.size());
          jobMsg.setRunNo(msg.getRunNo());

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

      // Update stage record via MQ
      statusPublisher.stageCompleted(msg.getPipelineRunId(),
          msg.getPipelineName(), msg.getRunNo(),
          stage.getStageName(), !stageFailed);

      eventPublisher.publishStageCompleted(msg.getPipelineRunId(),
          msg.getPipelineName(), msg.getRunNo(), stage.getStageName(),
          !stageFailed);

      if (pipelineFailed) {
        System.err.println("=== Pipeline FAILED ===");
        break;
      }
    }

    // Update pipeline record via MQ
    statusPublisher.pipelineCompleted(msg.getPipelineRunId(),
        msg.getPipelineName(), msg.getRunNo(), !pipelineFailed);

    eventPublisher.publishPipelineCompleted(msg.getPipelineRunId(),
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
}

package cicd.listener;

import cicd.config.RabbitMqConfig;
import cicd.executor.ExecutionPlanner;
import cicd.messaging.JobExecuteMessage;
import cicd.messaging.PipelineExecuteMessage;
import cicd.model.Job;
import cicd.model.Pipeline;
import cicd.observability.TraceContextHelper;
import cicd.parser.YamlParser;
import cicd.service.StageCoordinatorService;
import cicd.service.StatusEventPublisher;
import cicd.service.StatusUpdatePublisher;
import cicd.service.WorkspaceArchiveService;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
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

  private static final Logger log =
      LoggerFactory.getLogger(PipelineOrchestrationListener.class);

  private final RabbitTemplate rabbitTemplate;
  private final StageCoordinatorService coordinator;
  private final StatusEventPublisher eventPublisher;
  private final StatusUpdatePublisher statusPublisher;
  private final WorkspaceArchiveService workspaceArchiveService;
  private final Tracer tracer;

  /** Creates the orchestrator with all required dependencies. */
  public PipelineOrchestrationListener(
      RabbitTemplate rabbitTemplate,
      StageCoordinatorService coordinator,
      StatusEventPublisher eventPublisher,
      StatusUpdatePublisher statusPublisher,
      WorkspaceArchiveService workspaceArchiveService,
      Tracer tracer) {
    this.rabbitTemplate = rabbitTemplate;
    this.coordinator = coordinator;
    this.eventPublisher = eventPublisher;
    this.statusPublisher = statusPublisher;
    this.workspaceArchiveService = workspaceArchiveService;
    this.tracer = tracer;
  }

  /** Backward-compatible constructor for tests (no-op tracer). */
  public PipelineOrchestrationListener(
      RabbitTemplate rabbitTemplate,
      StageCoordinatorService coordinator,
      StatusEventPublisher eventPublisher,
      StatusUpdatePublisher statusPublisher,
      WorkspaceArchiveService workspaceArchiveService) {
    this(rabbitTemplate, coordinator, eventPublisher, statusPublisher,
        workspaceArchiveService,
        io.opentelemetry.api.OpenTelemetry.noop().getTracer("test"));
  }

  /** Orchestrates a full pipeline run from message to completion. */
  @RabbitListener(queues = RabbitMqConfig.PIPELINE_EXECUTE_QUEUE,
      concurrency = "1")
  public void onPipelineExecute(PipelineExecuteMessage msg,
      Message amqpMessage) {
    Path workspacePath = null;
    MDC.put("pipeline", msg.getPipelineName());
    MDC.put("run_no", String.valueOf(msg.getRunNo()));
    MDC.put("source", "system");

    // Extract parent context from incoming message headers
    Context parentCtx = TraceContextHelper.extractContext(
        amqpMessage.getMessageProperties().getHeaders());
    Span pipelineSpan = tracer.spanBuilder(
        "pipeline: " + msg.getPipelineName())
        .setParent(parentCtx)
        .setAttribute(AttributeKey.stringKey("pipeline"),
            msg.getPipelineName())
        .setAttribute(AttributeKey.longKey("run_no"), (long) msg.getRunNo())
        .startSpan();

    try (Scope pipelineScope = pipelineSpan.makeCurrent()) {
      log.info("Orchestrator: running pipeline '{}' run #{}",
          msg.getPipelineName(), msg.getRunNo());

      // Update to RUNNING via MQ
      statusPublisher.pipelineRunning(
          msg.getPipelineRunId(), msg.getPipelineName(), msg.getRunNo());

      eventPublisher.publishPipelineStarted(
          msg.getPipelineRunId(), msg.getPipelineName(), msg.getRunNo());

      workspacePath = workspaceArchiveService.extractArchive(
          msg.getWorkspaceArchive());

      // Parse pipeline from YAML
      Pipeline pipeline = parsePipelineYaml(msg.getPipelineYaml());
      if (pipeline == null) {
        pipelineSpan.setStatus(StatusCode.ERROR, "YAML parse failed");
        statusPublisher.pipelineFailed(
            msg.getPipelineRunId(), msg.getPipelineName(), msg.getRunNo());
        return;
      }

      // Compute wave-based execution plan
      ExecutionPlanner planner = new ExecutionPlanner(pipeline);
      List<ExecutionPlanner.WaveStageExecution> plan =
          planner.computeWaveOrder();

      boolean pipelineFailed = false;
      int stageOrder = 0;

      for (ExecutionPlanner.WaveStageExecution stage : plan) {
        MDC.put("stage", stage.getStageName());
        log.info("Stage: {}", stage.getStageName());

        Span stageSpan = tracer.spanBuilder(
            "stage: " + stage.getStageName()).startSpan();

        try (Scope stageScope = stageSpan.makeCurrent()) {
          int currentStageOrder = stageOrder++;

          statusPublisher.stageStarted(msg.getPipelineRunId(),
              msg.getPipelineName(), msg.getRunNo(),
              stage.getStageName(), currentStageOrder);

          eventPublisher.publishStageStarted(msg.getPipelineRunId(),
              msg.getPipelineName(), msg.getRunNo(), stage.getStageName());

          boolean stageFailed = false;
          Set<String> failedRequiredJobs = new HashSet<>();

          for (List<Job> wave : stage.getWaves()) {
            // Filter jobs: skip those whose needs include a failed required job
            List<Job> runnableJobs = new java.util.ArrayList<>();
            for (Job job : wave) {
              boolean blocked = job.needs.stream()
                  .anyMatch(failedRequiredJobs::contains);
              if (blocked) {
                // Mark skipped job as FAILED
                statusPublisher.jobCreated(msg.getPipelineRunId(),
                    msg.getPipelineName(), msg.getRunNo(),
                    stage.getStageName(), job.name, job.allowFailure);
                statusPublisher.jobCompleted(msg.getPipelineRunId(),
                    msg.getPipelineName(), msg.getRunNo(),
                    stage.getStageName(), job.name, false);
                if (!job.allowFailure) {
                  failedRequiredJobs.add(job.name);
                }
                log.info("Skipped job: {} (blocked by failed dependency)",
                    job.name);
              } else {
                runnableJobs.add(job);
              }
            }

            if (runnableJobs.isEmpty()) {
              continue;
            }

            String correlationId = UUID.randomUUID().toString();
            coordinator.registerWave(correlationId, runnableJobs.size());

            for (Job job : runnableJobs) {
              statusPublisher.jobCreated(msg.getPipelineRunId(),
                  msg.getPipelineName(), msg.getRunNo(),
                  stage.getStageName(), job.name, job.allowFailure);

              JobExecuteMessage jobMsg = new JobExecuteMessage();
              jobMsg.setPipelineRunId(msg.getPipelineRunId());
              jobMsg.setCorrelationId(correlationId);
              jobMsg.setJobName(job.name);
              jobMsg.setStageName(stage.getStageName());
              jobMsg.setPipelineName(msg.getPipelineName());
              jobMsg.setImage(job.image);
              jobMsg.setScripts(job.script);
              jobMsg.setWorkspacePath(workspacePath.toString());
              jobMsg.setTotalJobsInWave(runnableJobs.size());
              jobMsg.setRunNo(msg.getRunNo());
              jobMsg.setAllowFailure(job.allowFailure);

              // Stage span is current — RabbitTemplate auto-injects context
              rabbitTemplate.convertAndSend(
                  RabbitMqConfig.JOB_EXCHANGE,
                  RabbitMqConfig.JOB_EXECUTE_KEY,
                  jobMsg);

              log.info("Dispatched job: {} [{}] wave={}",
                  job.name, job.image, correlationId);
            }

            StageCoordinatorService.WaveTracker tracker =
                coordinator.awaitWave(correlationId);
            coordinator.removeWave(correlationId);

            if (tracker == null || tracker.isTimedOut()) {
              stageFailed = true;
              pipelineFailed = true;
              if (tracker != null && tracker.isTimedOut()) {
                log.error("Wave timed out in stage '{}'",
                    stage.getStageName());
              }
              break;
            }

            // Process results: only required job failures affect stage status
            for (cicd.messaging.JobResultMessage result
                : tracker.getResults()) {
              if (!result.isSuccess()) {
                if (!result.isAllowFailure()) {
                  failedRequiredJobs.add(result.getJobName());
                  stageFailed = true;
                  pipelineFailed = true;
                }
              }
            }

            // If a required job failed, break wave loop for this stage
            if (stageFailed) {
              break;
            }
          }

          statusPublisher.stageCompleted(msg.getPipelineRunId(),
              msg.getPipelineName(), msg.getRunNo(),
              stage.getStageName(), !stageFailed);

          eventPublisher.publishStageCompleted(msg.getPipelineRunId(),
              msg.getPipelineName(), msg.getRunNo(), stage.getStageName(),
              !stageFailed);

          if (stageFailed) {
            stageSpan.setStatus(StatusCode.ERROR, "stage failed");
          }
        } finally {
          stageSpan.end();
        }

        MDC.remove("stage");
        if (pipelineFailed) {
          log.error("Pipeline FAILED");
          break;
        }
      }

      statusPublisher.pipelineCompleted(msg.getPipelineRunId(),
          msg.getPipelineName(), msg.getRunNo(), !pipelineFailed);

      eventPublisher.publishPipelineCompleted(msg.getPipelineRunId(),
          msg.getPipelineName(), msg.getRunNo(), !pipelineFailed);

      if (pipelineFailed) {
        pipelineSpan.setStatus(StatusCode.ERROR, "pipeline failed");
      } else {
        log.info("Pipeline PASSED");
      }
    } finally {
      pipelineSpan.end();
      workspaceArchiveService.cleanupWorkspace(workspacePath);
      MDC.clear();
    }
  }

  /** Backward-compatible overload for tests (no AMQP headers). */
  public void onPipelineExecute(PipelineExecuteMessage msg) {
    onPipelineExecute(msg, new Message(new byte[0]));
  }

  private Pipeline parsePipelineYaml(String yamlContent) {
    try {
      Path tempFile = Files.createTempFile("pipeline-worker-", ".yaml");
      Files.writeString(tempFile, yamlContent);
      YamlParser parser = new YamlParser(tempFile.toString());
      Pipeline pipeline = parser.parse();
      Files.deleteIfExists(tempFile);

      if (!parser.getErrors().isEmpty()) {
        log.error("YAML parse errors: {}",
            String.join("\n", parser.getErrors()));
        return null;
      }
      return pipeline;
    } catch (IOException e) {
      log.error("Failed to parse pipeline YAML: {}", e.getMessage());
      return null;
    }
  }
}

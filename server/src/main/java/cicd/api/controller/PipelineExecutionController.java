package cicd.api.controller;

import cicd.api.dto.ExecutePipelineRequest;
import cicd.api.dto.ExecutePipelineResponse;
import cicd.messaging.PipelineExecuteMessage;
import cicd.messaging.PipelineMessagePublisher;
import cicd.model.Pipeline;
import cicd.parser.YamlParser;
import cicd.persistence.entity.PipelineRunEntity;
import cicd.persistence.entity.RunStatus;
import cicd.persistence.repository.PipelineRunRepository;
import cicd.service.GitRepositoryService;
import cicd.service.WorkspaceArchiveService;
import cicd.util.PipelineFinder;
import cicd.validator.Validator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoint for executing pipelines. */
@RestController
@RequestMapping("/api/pipelines")
@CrossOrigin(origins = "*")
public class PipelineExecutionController {

  @Autowired
  private PipelineRunRepository pipelineRunRepo;

  @Autowired
  private PipelineMessagePublisher messagePublisher;

  @Autowired
  private GitRepositoryService gitService;

  @Autowired
  private WorkspaceArchiveService workspaceArchiveService;

  @Autowired
  private Tracer tracer =
      io.opentelemetry.api.OpenTelemetry.noop().getTracer("cicd-server");

  /** Accepts a pipeline execution request and queues it. */
  @PostMapping("/execute")
  public ResponseEntity<?> executePipeline(
      @RequestBody ExecutePipelineRequest request) {
    Path snapshotPath = null;
    try {
      String repoUrl = request.getRepoUrl();
      if (repoUrl == null || repoUrl.isEmpty()) {
        return ResponseEntity.badRequest()
            .body("repoUrl must be provided");
      }

      Pipeline pipeline = null;
      String yamlContent = null;

      snapshotPath = gitService.createSnapshot(
          repoUrl, request.getBranch(), request.getCommit());
      String repoSnapshotDir = snapshotPath.toString();

      final String actualBranch = request.getBranch() != null
          && !request.getBranch().isBlank()
          ? request.getBranch()
          : gitService.getActualBranchName(snapshotPath);
      final String actualCommit =
          gitService.getActualCommitHash(snapshotPath);

      if (request.getPipelineName() != null
          && !request.getPipelineName().isEmpty()) {
        PipelineFinder.FindResult found =
            PipelineFinder.findByName(
                request.getPipelineName(), repoSnapshotDir);
        if (found.hasError()) {
          return ResponseEntity.badRequest()
              .body("Error finding pipeline: " + found.error);
        }
        pipeline = found.pipeline;
        yamlContent = Files.readString(Path.of(found.filePath));
      } else if (request.getPipelineYaml() != null
          && !request.getPipelineYaml().isEmpty()) {
        yamlContent = request.getPipelineYaml();
        Path tempFile =
            Files.createTempFile("pipeline-", ".yaml");
        try {
          Files.writeString(tempFile, yamlContent);
          YamlParser parser =
              new YamlParser(tempFile.toString());
          pipeline = parser.parse();
          if (!parser.getErrors().isEmpty()) {
            return ResponseEntity.badRequest()
                .body("YAML parsing errors: "
                    + String.join("\n", parser.getErrors()));
          }
        } finally {
          Files.deleteIfExists(tempFile);
        }
      } else {
        return ResponseEntity.badRequest().body(
            "Either pipelineName or pipelineYaml "
            + "must be provided");
      }

      Validator validator =
          new Validator("pipeline.yaml", pipeline);
      List<String> errors = validator.validate();
      if (!errors.isEmpty()) {
        return ResponseEntity.badRequest()
            .body("Validation errors: "
                + String.join("\n", errors));
      }

      // Create initiation span — its trace-id becomes the pipeline trace-id
      Span initiationSpan = tracer.spanBuilder(
          "pipeline.initiate: " + pipeline.name).startSpan();
      String traceId = initiationSpan.getSpanContext().getTraceId();

      PipelineRunEntity pipelineRun = new PipelineRunEntity();
      pipelineRun.setPipelineName(pipeline.name);
      pipelineRun.setRunNo(
          pipelineRunRepo.nextRunNo(pipeline.name));
      pipelineRun.setStatus(RunStatus.PENDING);
      pipelineRun.setStartTime(OffsetDateTime.now());
      pipelineRun.setGitHash(actualCommit);
      pipelineRun.setGitBranch(actualBranch);
      pipelineRun.setGitRepo(repoUrl);
      pipelineRun.setTraceId(traceId);
      pipelineRun = pipelineRunRepo.save(pipelineRun);
      pipelineRunRepo.flush();

      byte[] workspaceArchive =
          workspaceArchiveService.createArchive(snapshotPath);

      PipelineExecuteMessage message =
          new PipelineExecuteMessage(
              pipelineRun.getId(),
              pipeline.name,
              pipelineRun.getRunNo(),
              yamlContent,
              workspaceArchive,
              actualBranch,
              actualCommit,
              traceId
          );

      try (Scope ignored = initiationSpan.makeCurrent()) {
        messagePublisher.publishPipelineExecute(message);
      } catch (AmqpException ex) {
        pipelineRun.setStatus(RunStatus.FAILED);
        pipelineRun.setEndTime(OffsetDateTime.now());
        pipelineRunRepo.save(pipelineRun);
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body("Message queue unavailable: "
                + ex.getMessage());
      } finally {
        initiationSpan.end();
      }

      ExecutePipelineResponse response =
          ExecutePipelineResponse.fromEntity(pipelineRun);
      return ResponseEntity.accepted().body(response);

    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(ex.getMessage());
    } catch (IOException ex) {
      return ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Error processing pipeline: "
              + ex.getMessage());
    } catch (Exception ex) {
      return ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Unexpected error: " + ex.getMessage());
    } finally {
      if (snapshotPath != null) {
        gitService.cleanupSnapshot(snapshotPath);
      }
    }
  }
}

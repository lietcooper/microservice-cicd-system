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
import cicd.util.PipelineFinder;
import cicd.validator.Validator;
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

@RestController
@RequestMapping("/api/pipelines")
@CrossOrigin(origins = "*")
public class PipelineExecutionController {

    @Autowired
    private PipelineRunRepository pipelineRunRepo;

    @Autowired
    private PipelineMessagePublisher messagePublisher;

    @Autowired
    private cicd.service.GitRepositoryService gitService;

    @Autowired
    private cicd.service.WorkspaceArchiveService workspaceArchiveService;

    @PostMapping("/execute")
    public ResponseEntity<?> executePipeline(@RequestBody ExecutePipelineRequest request) {
        Path snapshotPath = null;
        try {
            String repoUrl = request.getRepoUrl();
            if (repoUrl == null || repoUrl.isEmpty()) {
                // If not provided, we might still fallback to current dir for local runs if allowed,
                // but usually repoUrl is mandatory for remote execution.
                return ResponseEntity.badRequest().body("repoUrl must be provided");
            }

            Pipeline pipeline = null;
            String yamlContent = null;

            // Always create a snapshot to get the latest code and find the pipeline
            snapshotPath = gitService.createSnapshot(repoUrl, request.getBranch(), request.getCommit());
            String repoSnapshotDir = snapshotPath.toString();

            // Resolve actual branch and commit from the snapshot
            String actualBranch = request.getBranch() != null && !request.getBranch().isBlank()
                ? request.getBranch() : gitService.getActualBranchName(snapshotPath);
            String actualCommit = gitService.getActualCommitHash(snapshotPath);

            // Parse pipeline from name or YAML content
            if (request.getPipelineName() != null && !request.getPipelineName().isEmpty()) {
                PipelineFinder.FindResult found = PipelineFinder.findByName(
                    request.getPipelineName(), repoSnapshotDir);
                if (found.hasError()) {
                    return ResponseEntity.badRequest()
                            .body("Error finding pipeline: " + found.error);
                }
                pipeline = found.pipeline;
                // Read YAML content from the found file
                yamlContent = Files.readString(Path.of(found.filePath));
            } else if (request.getPipelineYaml() != null && !request.getPipelineYaml().isEmpty()) {
                yamlContent = request.getPipelineYaml();
                // Validate the provided YAML by writing to a temp file
                Path tempFile = Files.createTempFile("pipeline-", ".yaml");
                try {
                    Files.writeString(tempFile, yamlContent);
                    YamlParser parser = new YamlParser(tempFile.toString());
                    pipeline = parser.parse();
                    if (!parser.getErrors().isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body("YAML parsing errors: " + String.join("\n", parser.getErrors()));
                    }
                } finally {
                    Files.deleteIfExists(tempFile);
                }
            } else {
                return ResponseEntity.badRequest()
                        .body("Either pipelineName or pipelineYaml must be provided");
            }

            // Validate pipeline
            Validator validator = new Validator("pipeline.yaml", pipeline);
            List<String> errors = validator.validate();
            if (!errors.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body("Validation errors: " + String.join("\n", errors));
            }

            // Create pipeline run record with PENDING status
            PipelineRunEntity pipelineRun = new PipelineRunEntity();
            pipelineRun.setPipelineName(pipeline.name);
            pipelineRun.setRunNo(pipelineRunRepo.nextRunNo(pipeline.name));
            pipelineRun.setStatus(RunStatus.PENDING);
            pipelineRun.setStartTime(OffsetDateTime.now());
            pipelineRun.setGitHash(actualCommit);
            pipelineRun.setGitBranch(actualBranch);
            pipelineRun.setGitRepo(repoUrl);
            pipelineRun = pipelineRunRepo.save(pipelineRun);
            pipelineRunRepo.flush();

            byte[] workspaceArchive = workspaceArchiveService.createArchive(snapshotPath);

            // Publish execution message to RabbitMQ.
            PipelineExecuteMessage message = new PipelineExecuteMessage(
                pipelineRun.getId(),
                pipeline.name,
                pipelineRun.getRunNo(),
                yamlContent,
                workspaceArchive,
                actualBranch,
                actualCommit
            );

            try {
                messagePublisher.publishPipelineExecute(message);
            } catch (AmqpException e) {
                // If RabbitMQ is unavailable, mark as FAILED
                pipelineRun.setStatus(RunStatus.FAILED);
                pipelineRun.setEndTime(OffsetDateTime.now());
                pipelineRunRepo.save(pipelineRun);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("Message queue unavailable: " + e.getMessage());
            }

            ExecutePipelineResponse response = ExecutePipelineResponse.fromEntity(pipelineRun);
            return ResponseEntity.accepted().body(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing pipeline: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        } finally {
            if (snapshotPath != null) {
                gitService.cleanupSnapshot(snapshotPath);
            }
        }
    }
}

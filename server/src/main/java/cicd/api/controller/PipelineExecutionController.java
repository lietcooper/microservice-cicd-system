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
import cicd.util.GitHelper;
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

    @PostMapping("/execute")
    public ResponseEntity<?> executePipeline(@RequestBody ExecutePipelineRequest request) {
        try {
            String repoPath = request.getRepoPath();
            if (repoPath == null || repoPath.isEmpty()) {
                repoPath = System.getProperty("user.dir");
            }

            Pipeline pipeline = null;
            String yamlFilePath = null;
            String yamlContent = null;

            // Parse pipeline from name or YAML content
            if (request.getPipelineName() != null && !request.getPipelineName().isEmpty()) {
                PipelineFinder.FindResult found = PipelineFinder.findByName(
                    request.getPipelineName(), repoPath);
                if (found.hasError()) {
                    return ResponseEntity.badRequest()
                            .body("Error finding pipeline: " + found.error);
                }
                pipeline = found.pipeline;
                yamlFilePath = found.filePath;
                // Read YAML content for the message
                yamlContent = Files.readString(Path.of(yamlFilePath));
            } else if (request.getPipelineYaml() != null && !request.getPipelineYaml().isEmpty()) {
                yamlContent = request.getPipelineYaml();
                Path tempFile = Files.createTempFile("pipeline-", ".yaml");
                Files.writeString(tempFile, yamlContent);
                yamlFilePath = tempFile.toString();

                YamlParser parser = new YamlParser(yamlFilePath);
                pipeline = parser.parse();

                if (!parser.getErrors().isEmpty()) {
                    Files.deleteIfExists(tempFile);
                    return ResponseEntity.badRequest()
                            .body("YAML parsing errors: " + String.join("\n", parser.getErrors()));
                }
                Files.deleteIfExists(tempFile);
            } else {
                return ResponseEntity.badRequest()
                        .body("Either pipelineName or pipelineYaml must be provided");
            }

            // Validate pipeline
            if (yamlFilePath != null) {
                Validator validator = new Validator(yamlFilePath, pipeline);
                List<String> errors = validator.validate();
                if (!errors.isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body("Validation errors: " + String.join("\n", errors));
                }
            }

            // Resolve git info
            String actualBranch = request.getBranch() != null
                ? request.getBranch() : GitHelper.currentBranch(repoPath);
            String actualCommit = request.getCommit() != null
                ? request.getCommit() : GitHelper.currentCommit(repoPath);

            // Create pipeline run record with PENDING status
            PipelineRunEntity pipelineRun = new PipelineRunEntity();
            pipelineRun.setPipelineName(pipeline.name);
            pipelineRun.setRunNo(pipelineRunRepo.nextRunNo(pipeline.name));
            pipelineRun.setStatus(RunStatus.PENDING);
            pipelineRun.setStartTime(OffsetDateTime.now());
            pipelineRun.setGitHash(actualCommit);
            pipelineRun.setGitBranch(actualBranch);
            pipelineRun.setGitRepo(repoPath);
            pipelineRun = pipelineRunRepo.save(pipelineRun);
            pipelineRunRepo.flush();

            // Publish execution message to RabbitMQ
            PipelineExecuteMessage message = new PipelineExecuteMessage(
                pipelineRun.getId(),
                pipeline.name,
                pipelineRun.getRunNo(),
                yamlContent,
                repoPath,
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

            // Return 202 Accepted with the queued pipeline info
            ExecutePipelineResponse response = ExecutePipelineResponse.fromEntity(pipelineRun);
            return ResponseEntity.accepted().body(response);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing pipeline: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }
}

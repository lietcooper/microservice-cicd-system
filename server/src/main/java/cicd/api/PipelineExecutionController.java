package cicd.api;

import cicd.api.dto.ExecutePipelineRequest;
import cicd.api.dto.ExecutePipelineResponse;
import cicd.model.Pipeline;
import cicd.parser.YamlParser;
import cicd.persistence.entity.PipelineRunEntity;
import cicd.service.PipelineExecutorService;
import cicd.util.PipelineFinder;
import cicd.validator.Validator;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
@CrossOrigin(origins = "*") // Allow CLI to call from any host
public class PipelineExecutionController {

    @Autowired
    private PipelineExecutorService executorService;

    @PostMapping("/execute")
    public ResponseEntity<?> executePipeline(@RequestBody ExecutePipelineRequest request) {
        try {
            String repoPath = request.getRepoPath();
            if (repoPath == null || repoPath.isEmpty()) {
                repoPath = System.getProperty("user.dir");
            }

            Pipeline pipeline = null;
            String yamlFilePath = null;

            // Parse pipeline from name or YAML content
            if (request.getPipelineName() != null && !request.getPipelineName().isEmpty()) {
                // Find pipeline by name
                PipelineFinder.FindResult found = PipelineFinder.findByName(request.getPipelineName());
                if (found.hasError()) {
                    return ResponseEntity.badRequest()
                            .body("Error finding pipeline: " + found.error);
                }
                pipeline = found.pipeline;
                yamlFilePath = found.filePath;
            } else if (request.getPipelineYaml() != null && !request.getPipelineYaml().isEmpty()) {
                // Parse pipeline from YAML content
                // Create a temporary file to parse the YAML
                Path tempFile = Files.createTempFile("pipeline-", ".yaml");
                Files.writeString(tempFile, request.getPipelineYaml());
                yamlFilePath = tempFile.toString();

                YamlParser parser = new YamlParser(yamlFilePath);
                pipeline = parser.parse();

                if (!parser.getErrors().isEmpty()) {
                    Files.deleteIfExists(tempFile);
                    return ResponseEntity.badRequest()
                            .body("YAML parsing errors: " + String.join("\n", parser.getErrors()));
                }

                // Clean up temp file after parsing
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

            // Execute pipeline with database persistence
            PipelineRunEntity pipelineRun = executorService.executePipeline(
                    pipeline,
                    repoPath,
                    request.getBranch(),
                    request.getCommit()
            );

            // Convert to response DTO
            ExecutePipelineResponse response = ExecutePipelineResponse.fromEntity(pipelineRun);

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing pipeline: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }
}
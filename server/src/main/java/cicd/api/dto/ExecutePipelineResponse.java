package cicd.api.dto;

import cicd.persistence.entity.PipelineRunEntity;
import cicd.persistence.entity.RunStatus;
import java.time.OffsetDateTime;
import lombok.Data;

/** Response body returned after pipeline execution. */
@Data
public class ExecutePipelineResponse {
  private Long runId;
  private String pipelineName;
  private int runNo;
  private RunStatus status;
  private OffsetDateTime startTime;
  private OffsetDateTime endTime;
  private String gitBranch;
  private String gitHash;
  private String message;

  /** Builds a response from a pipeline run entity. */
  public static ExecutePipelineResponse fromEntity(
      PipelineRunEntity entity) {
    ExecutePipelineResponse response = new ExecutePipelineResponse();
    response.setRunId(entity.getId());
    response.setPipelineName(entity.getPipelineName());
    response.setRunNo(entity.getRunNo());
    response.setStatus(entity.getStatus());
    response.setStartTime(entity.getStartTime());
    response.setEndTime(entity.getEndTime());
    response.setGitBranch(entity.getGitBranch());
    response.setGitHash(entity.getGitHash());

    if (entity.getStatus() == RunStatus.SUCCESS) {
      response.setMessage("Pipeline executed successfully");
    } else if (entity.getStatus() == RunStatus.FAILED) {
      response.setMessage("Pipeline execution failed");
    } else if (entity.getStatus() == RunStatus.PENDING) {
      response.setMessage("Pipeline execution queued");
    } else {
      response.setMessage("Pipeline is running");
    }

    return response;
  }
}

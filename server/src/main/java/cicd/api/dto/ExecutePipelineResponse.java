package cicd.api.dto;

import cicd.persistence.entity.RunStatus;
import java.time.OffsetDateTime;
import lombok.Data;

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

    public static ExecutePipelineResponse fromEntity(
            cicd.persistence.entity.PipelineRunEntity entity) {
        ExecutePipelineResponse response = new ExecutePipelineResponse();
        response.setRunId(entity.getId());
        response.setPipelineName(entity.getPipelineName());
        response.setRunNo(entity.getRunNo());
        response.setStatus(entity.getStatus());
        response.setStartTime(entity.getStartTime());
        response.setEndTime(entity.getEndTime());
        response.setGitBranch(entity.getGitBranch());
        response.setGitHash(entity.getGitHash());

        // Set message based on status
        if (entity.getStatus() == RunStatus.SUCCESS) {
            response.setMessage("Pipeline executed successfully");
        } else if (entity.getStatus() == RunStatus.FAILED) {
            response.setMessage("Pipeline execution failed");
        } else {
            response.setMessage("Pipeline is running");
        }

        return response;
    }
}
package cicd.api.dto;

import lombok.Data;

/** Request body for pipeline execution. */
@Data
public class ExecutePipelineRequest {
  private String pipelineName;
  private String pipelineYaml;
  private String branch;
  private String commit;
  private String repoUrl;
}

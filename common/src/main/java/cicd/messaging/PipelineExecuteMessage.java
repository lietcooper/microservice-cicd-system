package cicd.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Message published by the Server when a pipeline run is requested.
 * Consumed by the Pipeline Orchestrator in the Worker module.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PipelineExecuteMessage {

  private Long pipelineRunId;
  private String pipelineName;
  private int runNo;
  private String pipelineYaml;
  private String repoPath;
  private String gitBranch;
  private String gitCommit;

}

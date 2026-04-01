package cicd.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Message published by the Server when a pipeline run is requested.
 * Consumed by the Pipeline Orchestrator in the Worker module.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PipelineExecuteMessage {

  private Long pipelineRunId;
  private String pipelineName;
  private int runNo;
  private String pipelineYaml;
  private byte[] workspaceArchive;
  private String gitBranch;
  private String gitCommit;
  private String traceId;

  /** Backward-compatible constructor without traceId. */
  @SuppressWarnings("checkstyle:ParameterNumber")
  public PipelineExecuteMessage(Long pipelineRunId,
      String pipelineName, int runNo, String pipelineYaml,
      byte[] workspaceArchive, String gitBranch,
      String gitCommit) {
    this.pipelineRunId = pipelineRunId;
    this.pipelineName = pipelineName;
    this.runNo = runNo;
    this.pipelineYaml = pipelineYaml;
    this.workspaceArchive = workspaceArchive;
    this.gitBranch = gitBranch;
    this.gitCommit = gitCommit;
  }

  /** Full constructor including traceId. */
  @SuppressWarnings("checkstyle:ParameterNumber")
  public PipelineExecuteMessage(Long pipelineRunId,
      String pipelineName, int runNo, String pipelineYaml,
      byte[] workspaceArchive, String gitBranch,
      String gitCommit, String traceId) {
    this(pipelineRunId, pipelineName, runNo, pipelineYaml,
        workspaceArchive, gitBranch, gitCommit);
    this.traceId = traceId;
  }

}

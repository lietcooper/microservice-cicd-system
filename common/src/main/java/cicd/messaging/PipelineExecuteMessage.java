package cicd.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Message published by the Server when a pipeline run is requested.
 * Consumed by the Pipeline Orchestrator in the Worker module.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PipelineExecuteMessage {

  private Long pipelineRunId;
  private String pipelineName;
  private int runNo;
  private String pipelineYaml;
  private String repoPath;
  private String gitBranch;
  private String gitCommit;

  public PipelineExecuteMessage() {
  }

  public PipelineExecuteMessage(Long pipelineRunId, String pipelineName,
      int runNo, String pipelineYaml, String repoPath,
      String gitBranch, String gitCommit) {
    this.pipelineRunId = pipelineRunId;
    this.pipelineName = pipelineName;
    this.runNo = runNo;
    this.pipelineYaml = pipelineYaml;
    this.repoPath = repoPath;
    this.gitBranch = gitBranch;
    this.gitCommit = gitCommit;
  }

  public Long getPipelineRunId() {
    return pipelineRunId;
  }

  public void setPipelineRunId(Long pipelineRunId) {
    this.pipelineRunId = pipelineRunId;
  }

  public String getPipelineName() {
    return pipelineName;
  }

  public void setPipelineName(String pipelineName) {
    this.pipelineName = pipelineName;
  }

  public int getRunNo() {
    return runNo;
  }

  public void setRunNo(int runNo) {
    this.runNo = runNo;
  }

  public String getPipelineYaml() {
    return pipelineYaml;
  }

  public void setPipelineYaml(String pipelineYaml) {
    this.pipelineYaml = pipelineYaml;
  }

  public String getRepoPath() {
    return repoPath;
  }

  public void setRepoPath(String repoPath) {
    this.repoPath = repoPath;
  }

  public String getGitBranch() {
    return gitBranch;
  }

  public void setGitBranch(String gitBranch) {
    this.gitBranch = gitBranch;
  }

  public String getGitCommit() {
    return gitCommit;
  }

  public void setGitCommit(String gitCommit) {
    this.gitCommit = gitCommit;
  }
}

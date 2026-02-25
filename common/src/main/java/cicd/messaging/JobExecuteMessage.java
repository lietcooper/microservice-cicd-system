package cicd.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Message published by the Pipeline Orchestrator to execute a single job.
 * Consumed by Job Workers.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobExecuteMessage {

  private Long pipelineRunId;
  private Long stageRunId;
  private Long jobRunId;
  private String correlationId;
  private String jobName;
  private String stageName;
  private String pipelineName;
  private String image;
  private List<String> scripts;
  private String repoPath;
  private int totalJobsInWave;

  public JobExecuteMessage() {
  }

  public Long getPipelineRunId() {
    return pipelineRunId;
  }

  public void setPipelineRunId(Long pipelineRunId) {
    this.pipelineRunId = pipelineRunId;
  }

  public Long getStageRunId() {
    return stageRunId;
  }

  public void setStageRunId(Long stageRunId) {
    this.stageRunId = stageRunId;
  }

  public Long getJobRunId() {
    return jobRunId;
  }

  public void setJobRunId(Long jobRunId) {
    this.jobRunId = jobRunId;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public void setCorrelationId(String correlationId) {
    this.correlationId = correlationId;
  }

  public String getJobName() {
    return jobName;
  }

  public void setJobName(String jobName) {
    this.jobName = jobName;
  }

  public String getStageName() {
    return stageName;
  }

  public void setStageName(String stageName) {
    this.stageName = stageName;
  }

  public String getPipelineName() {
    return pipelineName;
  }

  public void setPipelineName(String pipelineName) {
    this.pipelineName = pipelineName;
  }

  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }

  public List<String> getScripts() {
    return scripts;
  }

  public void setScripts(List<String> scripts) {
    this.scripts = scripts;
  }

  public String getRepoPath() {
    return repoPath;
  }

  public void setRepoPath(String repoPath) {
    this.repoPath = repoPath;
  }

  public int getTotalJobsInWave() {
    return totalJobsInWave;
  }

  public void setTotalJobsInWave(int totalJobsInWave) {
    this.totalJobsInWave = totalJobsInWave;
  }
}

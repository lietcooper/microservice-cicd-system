package cicd.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Message published by a Job Worker after completing a job.
 * Consumed by the Pipeline Orchestrator for fan-in coordination.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobResultMessage {

  private Long pipelineRunId;
  private Long stageRunId;
  private Long jobRunId;
  private String correlationId;
  private String jobName;
  private String stageName;
  private String pipelineName;
  private boolean success;
  private int exitCode;
  private String output;

  public JobResultMessage() {
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

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public int getExitCode() {
    return exitCode;
  }

  public void setExitCode(int exitCode) {
    this.exitCode = exitCode;
  }

  public String getOutput() {
    return output;
  }

  public void setOutput(String output) {
    this.output = output;
  }
}

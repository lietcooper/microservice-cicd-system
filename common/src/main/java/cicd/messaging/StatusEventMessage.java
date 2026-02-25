package cicd.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.OffsetDateTime;

/**
 * Event message published to the topic exchange on every status change.
 * Used for monitoring and real-time notifications.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StatusEventMessage {

  private String eventType;
  private Long pipelineRunId;
  private String pipelineName;
  private int runNo;
  private String stageName;
  private String jobName;
  private String status;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
  private OffsetDateTime timestamp;

  private String message;

  public StatusEventMessage() {
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
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

  public String getStageName() {
    return stageName;
  }

  public void setStageName(String stageName) {
    this.stageName = stageName;
  }

  public String getJobName() {
    return jobName;
  }

  public void setJobName(String jobName) {
    this.jobName = jobName;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public OffsetDateTime getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(OffsetDateTime timestamp) {
    this.timestamp = timestamp;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}

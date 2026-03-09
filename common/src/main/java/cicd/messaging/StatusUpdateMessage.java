package cicd.messaging;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Message sent from Worker to Server to update pipeline/stage/job status.
 * Server is the sole writer to the database.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StatusUpdateMessage {

  /** "PIPELINE", "STAGE", or "JOB" */
  private String entityType;

  private Long pipelineRunId;
  private String pipelineName;
  private int runNo;

  /** Null for pipeline-level updates. */
  private String stageName;

  /** Needed when creating a stage record. */
  private Integer stageOrder;

  /** Null for pipeline/stage-level updates. */
  private String jobName;

  /** "PENDING", "RUNNING", "SUCCESS", "FAILED" */
  private String status;

  @JsonFormat(shape = JsonFormat.Shape.STRING,
      pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
  private OffsetDateTime startTime;

  @JsonFormat(shape = JsonFormat.Shape.STRING,
      pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
  private OffsetDateTime endTime;

}

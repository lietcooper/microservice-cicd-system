package cicd.messaging;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.List;
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

  /** Entity type: "PIPELINE", "STAGE", or "JOB". */
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

  /** Status value: "PENDING", "RUNNING", "SUCCESS", "FAILED". */
  private String status;

  @JsonFormat(shape = JsonFormat.Shape.STRING,
      pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
  private OffsetDateTime startTime;

  @JsonFormat(shape = JsonFormat.Shape.STRING,
      pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
  private OffsetDateTime endTime;

  private Boolean allowFailure;

  /** Trace ID for pipeline-level updates; null for stage/job updates. */
  private String traceId;

  /** Artifact patterns from the YAML config (for job-level updates). */
  private List<String> artifactPatterns;

  /** Storage paths of collected artifacts (for job completion updates). */
  private List<String> artifactStoragePaths;

}

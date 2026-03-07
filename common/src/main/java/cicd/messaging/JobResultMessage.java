package cicd.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Message published by a Job Worker after completing a job.
 * Consumed by the Pipeline Orchestrator for fan-in coordination.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobResultMessage {

  private Long pipelineRunId;
  private String correlationId;
  private String jobName;
  private String stageName;
  private String pipelineName;
  private boolean success;
  private int exitCode;
  private String output;

}

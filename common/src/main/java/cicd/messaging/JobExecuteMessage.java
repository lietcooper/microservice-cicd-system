package cicd.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Message published by the Pipeline Orchestrator to execute a single job.
 * Consumed by Job Workers.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobExecuteMessage {

  private Long pipelineRunId;
  private String correlationId;
  private String jobName;
  private String stageName;
  private String pipelineName;
  private int runNo;
  private String image;
  private List<String> scripts;
  private String workspacePath;
  private int totalJobsInWave;

}

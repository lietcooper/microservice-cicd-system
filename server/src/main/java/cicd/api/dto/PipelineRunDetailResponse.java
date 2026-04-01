package cicd.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;

/**
 * Shared response for levels 2-4 reports.
 *
 * <ul>
 *   <li>Level 2: stages list, each without jobs</li>
 *   <li>Level 3: single stage with its jobs</li>
 *   <li>Level 4: single stage with a single job</li>
 * </ul>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PipelineRunDetailResponse {

  private String pipelineName;

  @JsonProperty("run-no")
  private int runNo;

  private String status;

  @JsonProperty("git-repo")
  private String gitRepo;

  @JsonProperty("git-branch")
  private String gitBranch;

  @JsonProperty("git-hash")
  private String gitHash;

  @JsonProperty("start")
  private OffsetDateTime startTime;

  @JsonProperty("end")
  private OffsetDateTime endTime;

  @JsonProperty("trace-id")
  private String traceId;

  private List<StageDto> stages;
}

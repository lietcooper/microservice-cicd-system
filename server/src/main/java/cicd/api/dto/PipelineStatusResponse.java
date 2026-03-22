package cicd.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;

/** Pipeline run status with stages and jobs. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PipelineStatusResponse {

  @JsonProperty("pipeline-name")
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

  private List<StageStatusDto> stages;
}

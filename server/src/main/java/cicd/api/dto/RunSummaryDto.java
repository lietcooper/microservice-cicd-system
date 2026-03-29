package cicd.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import lombok.Data;

/** Summary of a single pipeline run (used in level-1 report). */
@Data
public class RunSummaryDto {

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
}

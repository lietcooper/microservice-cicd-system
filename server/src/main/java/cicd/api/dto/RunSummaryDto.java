package cicd.api.dto;

import java.time.OffsetDateTime;
import lombok.Data;

/** Summary of a single pipeline run (used in level-1 report). */
@Data
public class RunSummaryDto {

  private int runNo;
  private String status;
  private String gitRepo;
  private String gitBranch;
  private String gitHash;
  private OffsetDateTime startTime;
  private OffsetDateTime endTime;
}

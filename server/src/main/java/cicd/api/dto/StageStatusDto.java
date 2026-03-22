package cicd.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;

/** Stage status with computed status from jobs. */
@Data
public class StageStatusDto {

  private String name;
  private String status;

  @JsonProperty("start")
  private OffsetDateTime startTime;

  @JsonProperty("end")
  private OffsetDateTime endTime;

  private List<JobStatusDto> jobs;
}

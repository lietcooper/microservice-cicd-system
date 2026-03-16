package cicd.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import lombok.Data;

/** A single job's execution info. */
@Data
public class JobDto {

  private String name;
  private String status;

  @JsonProperty("start")
  private OffsetDateTime startTime;

  @JsonProperty("end")
  private OffsetDateTime endTime;

  @JsonProperty("failures")
  private boolean failures;
}

package cicd.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import lombok.Data;

/** Job status within a stage. */
@Data
public class JobStatusDto {

  private String name;
  private String status;

  @JsonProperty("start")
  private OffsetDateTime startTime;

  @JsonProperty("end")
  private OffsetDateTime endTime;
}

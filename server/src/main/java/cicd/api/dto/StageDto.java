package cicd.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;

/** A single stage's execution info, optionally including its jobs. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StageDto {

  private String name;
  private String status;

  @JsonProperty("start")
  private OffsetDateTime startTime;

  @JsonProperty("end")
  private OffsetDateTime endTime;

  private List<JobDto> jobs;
}

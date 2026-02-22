package cicd.api.dto;

import java.time.OffsetDateTime;
import lombok.Data;

/** A single job's execution info. */
@Data
public class JobDto {

  private String name;
  private String status;
  private OffsetDateTime startTime;
  private OffsetDateTime endTime;
}

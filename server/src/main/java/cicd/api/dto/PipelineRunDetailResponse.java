package cicd.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
  private int runNo;
  private String status;
  private OffsetDateTime startTime;
  private OffsetDateTime endTime;
  private List<StageDto> stages;
}

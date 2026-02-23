package cicd.api.dto;

import java.util.List;
import lombok.Data;

/** Level-1 report: all runs for a pipeline. */
@Data
public class PipelineRunsResponse {

  private String pipelineName;
  private List<RunSummaryDto> runs;
}

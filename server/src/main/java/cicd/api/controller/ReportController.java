package cicd.api.controller;

import cicd.api.dto.PipelineRunDetailResponse;
import cicd.api.dto.PipelineRunsResponse;
import cicd.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for pipeline execution reports. */
@RestController
@RequestMapping("/pipelines")
public class ReportController {

  @Autowired
  private ReportService reportService;

  /**
   * Level 1: all runs for a pipeline.
   *
   * @param name pipeline name
   * @return list of run summaries
   */
  @GetMapping("/{name}/runs")
  public PipelineRunsResponse getAllRuns(
      @PathVariable String name) {
    return reportService.getAllRuns(name);
  }

  /**
   * Level 2: a specific run with its stages.
   *
   * @param name pipeline name
   * @param runNo run number
   * @return run detail with stage summaries
   */
  @GetMapping("/{name}/runs/{runNo}")
  public PipelineRunDetailResponse getRun(
      @PathVariable String name,
      @PathVariable int runNo) {
    return reportService.getRun(name, runNo);
  }

  /**
   * Level 3: a specific stage with its jobs.
   *
   * @param name pipeline name
   * @param runNo run number
   * @param stageName stage name
   * @return run detail with single stage and its jobs
   */
  @GetMapping("/{name}/runs/{runNo}/stages/{stageName}")
  public PipelineRunDetailResponse getStage(
      @PathVariable String name,
      @PathVariable int runNo,
      @PathVariable String stageName) {
    return reportService.getStage(name, runNo, stageName);
  }

  /**
   * Level 4: a specific job within a stage.
   *
   * @param name pipeline name
   * @param runNo run number
   * @param stageName stage name
   * @param jobName job name
   * @return run detail with single stage and single job
   */
  @GetMapping("/{name}/runs/{runNo}/stages/{stageName}/jobs/{jobName}")
  public PipelineRunDetailResponse getJob(
      @PathVariable String name,
      @PathVariable int runNo,
      @PathVariable String stageName,
      @PathVariable String jobName) {
    return reportService.getJob(name, runNo, stageName, jobName);
  }
}

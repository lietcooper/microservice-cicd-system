package cicd.api.controller;

import cicd.api.dto.PipelineStatusResponse;
import cicd.service.StatusService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for pipeline status queries. */
@RestController
@RequestMapping("/pipelines")
public class StatusController {

  @Autowired
  private StatusService statusService;

  /** Returns status of all pipeline runs for a repo. */
  @GetMapping("/status")
  public List<PipelineStatusResponse> getStatusByRepo(
      @RequestParam("repo") String repoUrl) {
    return statusService.getStatusByRepo(repoUrl);
  }

  /** Returns status of a specific pipeline run. */
  @GetMapping("/{name}/status")
  public PipelineStatusResponse getRunStatus(
      @PathVariable String name,
      @RequestParam("run") int runNo) {
    return statusService.getRunStatus(name, runNo);
  }
}

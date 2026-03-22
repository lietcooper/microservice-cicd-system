package cicd.service;

import cicd.api.dto.JobStatusDto;
import cicd.api.dto.PipelineStatusResponse;
import cicd.api.dto.StageStatusDto;
import cicd.exception.ResourceNotFoundException;
import cicd.persistence.entity.JobRunEntity;
import cicd.persistence.entity.PipelineRunEntity;
import cicd.persistence.entity.RunStatus;
import cicd.persistence.entity.StageRunEntity;
import cicd.persistence.repository.PipelineRunRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Provides live pipeline status with computed stage statuses. */
@Service
@Transactional(readOnly = true)
public class StatusService {

  @Autowired
  private PipelineRunRepository pipelineRunRepository;

  /**
   * Returns status of all pipeline runs for a repo.
   * Prefers currently running runs; falls back to most recent.
   */
  public List<PipelineStatusResponse> getStatusByRepo(
      String repoUrl) {
    List<PipelineRunEntity> runs =
        pipelineRunRepository.findByGitRepoAndStatus(
            repoUrl, RunStatus.RUNNING);
    if (runs.isEmpty()) {
      List<PipelineRunEntity> all =
          pipelineRunRepository.findByGitRepoOrderByRunNoDesc(
              repoUrl);
      if (!all.isEmpty()) {
        runs = List.of(all.get(0));
      }
    }
    return runs.stream()
        .map(this::toStatusResponse)
        .collect(Collectors.toList());
  }

  /** Returns status of a specific pipeline run. */
  public PipelineStatusResponse getRunStatus(
      String pipelineName, int runNo) {
    PipelineRunEntity run = pipelineRunRepository
        .findByPipelineNameAndRunNo(pipelineName, runNo)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Pipeline run not found: "
            + pipelineName + " #" + runNo));
    return toStatusResponse(run);
  }

  private PipelineStatusResponse toStatusResponse(
      PipelineRunEntity run) {
    PipelineStatusResponse resp = new PipelineStatusResponse();
    resp.setPipelineName(run.getPipelineName());
    resp.setRunNo(run.getRunNo());
    resp.setStatus(capitalize(run.getStatus()));
    resp.setGitRepo(run.getGitRepo());
    resp.setGitBranch(run.getGitBranch());
    resp.setGitHash(run.getGitHash());
    resp.setStartTime(run.getStartTime());
    resp.setEndTime(run.getEndTime());
    resp.setStages(run.getStageRuns().stream()
        .map(this::toStageStatusDto)
        .collect(Collectors.toList()));
    return resp;
  }

  private StageStatusDto toStageStatusDto(StageRunEntity stage) {
    StageStatusDto dto = new StageStatusDto();
    dto.setName(stage.getStageName());
    dto.setStartTime(stage.getStartTime());
    dto.setEndTime(stage.getEndTime());

    List<JobStatusDto> jobs = stage.getJobRuns().stream()
        .map(this::toJobStatusDto)
        .collect(Collectors.toList());
    dto.setJobs(jobs);
    dto.setStatus(computeStageStatus(stage.getJobRuns()));
    return dto;
  }

  private JobStatusDto toJobStatusDto(JobRunEntity job) {
    JobStatusDto dto = new JobStatusDto();
    dto.setName(job.getJobName());
    dto.setStatus(capitalize(job.getStatus()));
    dto.setStartTime(job.getStartTime());
    dto.setEndTime(job.getEndTime());
    return dto;
  }

  /** Computes stage status from job statuses. */
  String computeStageStatus(List<JobRunEntity> jobs) {
    if (jobs.isEmpty()) {
      return "Pending";
    }
    boolean allSuccess = true;
    boolean allPending = true;
    boolean anyFailed = false;
    boolean anyRunning = false;

    for (JobRunEntity job : jobs) {
      RunStatus s = job.getStatus();
      if (s != RunStatus.SUCCESS) {
        allSuccess = false;
      }
      if (s != RunStatus.PENDING) {
        allPending = false;
      }
      if (s == RunStatus.FAILED) {
        anyFailed = true;
      }
      if (s == RunStatus.RUNNING) {
        anyRunning = true;
      }
    }

    if (allSuccess) {
      return "Success";
    }
    if (allPending) {
      return "Pending";
    }
    if (anyFailed) {
      return "Failed";
    }
    if (anyRunning) {
      return "Running";
    }
    return "Pending";
  }

  private String capitalize(RunStatus status) {
    String name = status.name().toLowerCase();
    return name.substring(0, 1).toUpperCase() + name.substring(1);
  }
}

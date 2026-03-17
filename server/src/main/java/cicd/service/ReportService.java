package cicd.service;

import cicd.api.dto.JobDto;
import cicd.api.dto.PipelineRunDetailResponse;
import cicd.api.dto.PipelineRunsResponse;
import cicd.api.dto.RunSummaryDto;
import cicd.api.dto.StageDto;
import cicd.exception.ResourceNotFoundException;
import cicd.persistence.entity.JobRunEntity;
import cicd.persistence.entity.PipelineRunEntity;
import cicd.persistence.entity.StageRunEntity;
import cicd.persistence.repository.JobRunRepository;
import cicd.persistence.repository.PipelineRunRepository;
import cicd.persistence.repository.StageRunRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assembles pipeline report data from the database. */
@Service
@Transactional(readOnly = true)
public class ReportService {

  @Autowired
  private PipelineRunRepository pipelineRunRepository;

  @Autowired
  private StageRunRepository stageRunRepository;

  @Autowired
  private JobRunRepository jobRunRepository;

  /**
   * Get all runs for a pipeline.
   */
  public PipelineRunsResponse getAllRuns(String pipelineName) {
    List<PipelineRunEntity> runs = pipelineRunRepository
        .findByPipelineNameOrderByRunNoAsc(pipelineName);

    PipelineRunsResponse response = new PipelineRunsResponse();
    response.setPipelineName(pipelineName);
    response.setRuns(runs.stream()
        .map(this::toRunSummary)
        .collect(Collectors.toList()));

    return response;
  }

  /**
   * Get a specific run with its stages (Level 2).
   */
  public PipelineRunDetailResponse getRun(
      String pipelineName, int runNo) {
    PipelineRunEntity run = pipelineRunRepository
        .findByPipelineNameAndRunNo(pipelineName, runNo)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Pipeline run not found: "
            + pipelineName + " #" + runNo));

    PipelineRunDetailResponse response =
        new PipelineRunDetailResponse();
    response.setPipelineName(run.getPipelineName());
    response.setRunNo(run.getRunNo());
    response.setStatus(run.getStatus().toString());
    response.setGitRepo(run.getGitRepo());
    response.setGitBranch(run.getGitBranch());
    response.setGitHash(run.getGitHash());
    response.setStartTime(run.getStartTime());
    response.setEndTime(run.getEndTime());

    List<StageRunEntity> stages = stageRunRepository
        .findByPipelineRunIdOrderByStageOrderAsc(run.getId());
    response.setStages(stages.stream()
        .map(stage -> toStageDto(stage, false))
        .collect(Collectors.toList()));

    return response;
  }

  /**
   * Get a specific stage with its jobs (Level 3).
   */
  public PipelineRunDetailResponse getStage(
      String pipelineName, int runNo, String stageName) {
    PipelineRunEntity run = pipelineRunRepository
        .findByPipelineNameAndRunNo(pipelineName, runNo)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Pipeline run not found: "
            + pipelineName + " #" + runNo));

    PipelineRunDetailResponse response =
        new PipelineRunDetailResponse();
    response.setPipelineName(run.getPipelineName());
    response.setRunNo(run.getRunNo());
    response.setStatus(run.getStatus().toString());
    response.setGitRepo(run.getGitRepo());
    response.setGitBranch(run.getGitBranch());
    response.setGitHash(run.getGitHash());
    response.setStartTime(run.getStartTime());
    response.setEndTime(run.getEndTime());

    StageRunEntity stageRun = stageRunRepository
        .findByPipelineRunIdAndStageName(
            run.getId(), stageName)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Stage not found: " + stageName
            + " in run #" + runNo));

    List<StageDto> stages = new ArrayList<>();
    stages.add(toStageDto(stageRun, true));
    response.setStages(stages);

    return response;
  }

  /**
   * Get a specific job within a stage (Level 4).
   */
  public PipelineRunDetailResponse getJob(
      String pipelineName, int runNo,
      String stageName, String jobName) {
    PipelineRunEntity run = pipelineRunRepository
        .findByPipelineNameAndRunNo(pipelineName, runNo)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Pipeline run not found: "
            + pipelineName + " #" + runNo));

    PipelineRunDetailResponse response =
        new PipelineRunDetailResponse();
    response.setPipelineName(run.getPipelineName());
    response.setRunNo(run.getRunNo());
    response.setStatus(run.getStatus().toString());
    response.setGitRepo(run.getGitRepo());
    response.setGitBranch(run.getGitBranch());
    response.setGitHash(run.getGitHash());
    response.setStartTime(run.getStartTime());
    response.setEndTime(run.getEndTime());

    StageRunEntity stageRun = stageRunRepository
        .findByPipelineRunIdAndStageName(
            run.getId(), stageName)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Stage not found: " + stageName
            + " in run #" + runNo));

    final JobRunEntity jobRun = stageRun.getJobRuns().stream()
        .filter(job -> job.getJobName().equals(jobName))
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Job not found: " + jobName
            + " in stage " + stageName));

    StageDto stageDto = new StageDto();
    stageDto.setName(stageRun.getStageName());
    stageDto.setStatus(stageRun.getStatus().toString());
    stageDto.setStartTime(stageRun.getStartTime());
    stageDto.setEndTime(stageRun.getEndTime());

    List<JobDto> jobs = new ArrayList<>();
    jobs.add(toJobDto(jobRun));
    stageDto.setJobs(jobs);

    List<StageDto> stages = new ArrayList<>();
    stages.add(stageDto);
    response.setStages(stages);

    return response;
  }

  private RunSummaryDto toRunSummary(PipelineRunEntity entity) {
    RunSummaryDto dto = new RunSummaryDto();
    dto.setRunNo(entity.getRunNo());
    dto.setStatus(entity.getStatus().toString());
    dto.setGitRepo(entity.getGitRepo());
    dto.setGitBranch(entity.getGitBranch());
    dto.setGitHash(entity.getGitHash());
    dto.setStartTime(entity.getStartTime());
    dto.setEndTime(entity.getEndTime());
    return dto;
  }

  /**
   * Converts a stage entity to a DTO.
   *
   * @param includeJobs whether to include job details
   */
  private StageDto toStageDto(StageRunEntity entity,
      boolean includeJobs) {
    StageDto dto = new StageDto();
    dto.setName(entity.getStageName());
    dto.setStatus(entity.getStatus().toString());
    dto.setStartTime(entity.getStartTime());
    dto.setEndTime(entity.getEndTime());

    if (includeJobs) {
      dto.setJobs(entity.getJobRuns().stream()
          .map(this::toJobDto)
          .collect(Collectors.toList()));
    }

    return dto;
  }

  private JobDto toJobDto(JobRunEntity entity) {
    JobDto dto = new JobDto();
    dto.setName(entity.getJobName());
    dto.setStatus(entity.getStatus().toString());
    dto.setStartTime(entity.getStartTime());
    dto.setEndTime(entity.getEndTime());
    dto.setFailures(entity.isAllowFailure());
    return dto;
  }
}

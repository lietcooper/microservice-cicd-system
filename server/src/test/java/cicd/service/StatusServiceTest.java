package cicd.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cicd.api.dto.PipelineStatusResponse;
import cicd.exception.ResourceNotFoundException;
import cicd.persistence.entity.JobRunEntity;
import cicd.persistence.entity.PipelineRunEntity;
import cicd.persistence.entity.RunStatus;
import cicd.persistence.entity.StageRunEntity;
import cicd.persistence.repository.JobRunRepository;
import cicd.persistence.repository.PipelineRunRepository;
import cicd.persistence.repository.StageRunRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/** Tests for StatusService using H2 in-memory database. */
@DataJpaTest
@ActiveProfiles("test")
@EnableAutoConfiguration
@ContextConfiguration(classes = {
    StatusServiceTest.class,
    cicd.persistence.config.JpaConfig.class
})
@Import(StatusService.class)
class StatusServiceTest {

  @Autowired
  private StatusService statusService;

  @Autowired
  private PipelineRunRepository pipelineRunRepo;

  private static final String REPO =
      "https://github.com/org/repo";

  @Test
  void getStatusByRepoReturnsEmptyWhenNoRuns() {
    List<PipelineStatusResponse> result =
        statusService.getStatusByRepo("https://no-such-repo");
    assertTrue(result.isEmpty());
  }

  @Test
  void getStatusByRepoReturnsRunningRuns() {
    saveRun("pipe1", 1, RunStatus.SUCCESS, REPO);
    saveRun("pipe1", 2, RunStatus.RUNNING, REPO);

    List<PipelineStatusResponse> result =
        statusService.getStatusByRepo(REPO);
    assertEquals(1, result.size());
    assertEquals("Running", result.get(0).getStatus());
    assertEquals(2, result.get(0).getRunNo());
  }

  @Test
  void getStatusByRepoFallsBackToLatestCompleted() {
    saveRun("pipe1", 1, RunStatus.SUCCESS, REPO);
    saveRun("pipe1", 2, RunStatus.FAILED, REPO);

    List<PipelineStatusResponse> result =
        statusService.getStatusByRepo(REPO);
    assertEquals(1, result.size());
    assertEquals(2, result.get(0).getRunNo());
  }

  @Test
  void getStatusByRepoIncludesStagesAndJobs() {
    PipelineRunEntity run = buildRunWithStages(
        "pipe1", 1, RunStatus.RUNNING, REPO);
    pipelineRunRepo.save(run);

    List<PipelineStatusResponse> result =
        statusService.getStatusByRepo(REPO);
    assertEquals(1, result.size());
    assertNotNull(result.get(0).getStages());
    assertEquals(1, result.get(0).getStages().size());
    assertEquals("build",
        result.get(0).getStages().get(0).getName());
    assertNotNull(
        result.get(0).getStages().get(0).getJobs());
  }

  @Test
  void getRunStatusThrowsWhenNotFound() {
    assertThrows(ResourceNotFoundException.class,
        () -> statusService.getRunStatus("ghost", 99));
  }

  @Test
  void getRunStatusReturnsCorrectRun() {
    saveRun("mypipe", 1, RunStatus.SUCCESS, REPO);

    PipelineStatusResponse result =
        statusService.getRunStatus("mypipe", 1);
    assertEquals("mypipe", result.getPipelineName());
    assertEquals(1, result.getRunNo());
    assertEquals("Success", result.getStatus());
  }

  @Test
  void getRunStatusUsesCapitalizedStatuses() {
    saveRun("cappipe", 1, RunStatus.FAILED, REPO);

    PipelineStatusResponse result =
        statusService.getRunStatus("cappipe", 1);
    assertEquals("Failed", result.getStatus());
  }

  @Test
  void computeStageStatusAllSuccess() {
    List<JobRunEntity> jobs = makeJobs(
        RunStatus.SUCCESS, RunStatus.SUCCESS);
    assertEquals("Success",
        statusService.computeStageStatus(jobs));
  }

  @Test
  void computeStageStatusAllPending() {
    List<JobRunEntity> jobs = makeJobs(
        RunStatus.PENDING, RunStatus.PENDING);
    assertEquals("Pending",
        statusService.computeStageStatus(jobs));
  }

  @Test
  void computeStageStatusAnyFailed() {
    List<JobRunEntity> jobs = makeJobs(
        RunStatus.SUCCESS, RunStatus.FAILED);
    assertEquals("Failed",
        statusService.computeStageStatus(jobs));
  }

  @Test
  void computeStageStatusAnyRunning() {
    List<JobRunEntity> jobs = makeJobs(
        RunStatus.RUNNING, RunStatus.PENDING);
    assertEquals("Running",
        statusService.computeStageStatus(jobs));
  }

  @Test
  void computeStageStatusRunningAndSuccess() {
    List<JobRunEntity> jobs = makeJobs(
        RunStatus.RUNNING, RunStatus.SUCCESS);
    assertEquals("Running",
        statusService.computeStageStatus(jobs));
  }

  @Test
  void computeStageStatusEmpty() {
    assertEquals("Pending",
        statusService.computeStageStatus(List.of()));
  }

  @Test
  void stageStatusComputedFromJobsNotEntity() {
    PipelineRunEntity run = new PipelineRunEntity();
    run.setPipelineName("computed");
    run.setRunNo(1);
    run.setStatus(RunStatus.RUNNING);
    run.setGitRepo(REPO);
    run.setStartTime(OffsetDateTime.now());

    StageRunEntity stage = new StageRunEntity();
    stage.setStageName("build");
    stage.setStageOrder(0);
    stage.setStatus(RunStatus.RUNNING);
    stage.setStartTime(OffsetDateTime.now());
    run.addStage(stage);

    JobRunEntity job = new JobRunEntity();
    job.setJobName("compile");
    job.setStatus(RunStatus.SUCCESS);
    job.setStartTime(OffsetDateTime.now());
    job.setEndTime(OffsetDateTime.now());
    stage.addJob(job);

    pipelineRunRepo.save(run);

    PipelineStatusResponse result =
        statusService.getRunStatus("computed", 1);
    assertEquals("Success",
        result.getStages().get(0).getStatus());
  }

  private PipelineRunEntity saveRun(String name, int runNo,
      RunStatus status, String repo) {
    PipelineRunEntity run = new PipelineRunEntity();
    run.setPipelineName(name);
    run.setRunNo(runNo);
    run.setStatus(status);
    run.setGitRepo(repo);
    run.setStartTime(OffsetDateTime.now());
    if (status != RunStatus.RUNNING) {
      run.setEndTime(OffsetDateTime.now());
    }
    return pipelineRunRepo.save(run);
  }

  private PipelineRunEntity buildRunWithStages(String name,
      int runNo, RunStatus status, String repo) {
    PipelineRunEntity run = new PipelineRunEntity();
    run.setPipelineName(name);
    run.setRunNo(runNo);
    run.setStatus(status);
    run.setGitRepo(repo);
    run.setStartTime(OffsetDateTime.now());

    StageRunEntity stage = new StageRunEntity();
    stage.setStageName("build");
    stage.setStageOrder(0);
    stage.setStatus(RunStatus.RUNNING);
    stage.setStartTime(OffsetDateTime.now());
    run.addStage(stage);

    JobRunEntity job = new JobRunEntity();
    job.setJobName("compile");
    job.setStatus(RunStatus.RUNNING);
    job.setStartTime(OffsetDateTime.now());
    stage.addJob(job);

    return run;
  }

  private List<JobRunEntity> makeJobs(RunStatus... statuses) {
    List<JobRunEntity> jobs = new ArrayList<>();
    for (RunStatus s : statuses) {
      JobRunEntity job = new JobRunEntity();
      job.setJobName("job-" + jobs.size());
      job.setStatus(s);
      jobs.add(job);
    }
    return jobs;
  }
}

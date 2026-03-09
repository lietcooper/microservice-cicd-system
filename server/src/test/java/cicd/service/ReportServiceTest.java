package cicd.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cicd.api.dto.PipelineRunDetailResponse;
import cicd.api.dto.PipelineRunsResponse;
import cicd.exception.ResourceNotFoundException;
import cicd.persistence.entity.JobRunEntity;
import cicd.persistence.entity.PipelineRunEntity;
import cicd.persistence.entity.RunStatus;
import cicd.persistence.entity.StageRunEntity;
import cicd.persistence.repository.JobRunRepository;
import cicd.persistence.repository.PipelineRunRepository;
import cicd.persistence.repository.StageRunRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * Tests for ReportService using an in-memory H2 database.
 * Verifies the mapping from entities to DTOs at each report level.
 */
@DataJpaTest
@ActiveProfiles("test")
@EnableAutoConfiguration
@ContextConfiguration(classes = {
    ReportServiceTest.class,
    cicd.persistence.config.JpaConfig.class
})
@Import(ReportService.class)
class ReportServiceTest {

  @Autowired
  private ReportService reportService;

  @Autowired
  private PipelineRunRepository pipelineRunRepo;

  @Autowired
  private StageRunRepository stageRunRepo;

  @Autowired
  private JobRunRepository jobRunRepo;

  @Test
  void getAllRunsReturnsEmptyWhenNoneExist() {
    PipelineRunsResponse response =
        reportService.getAllRuns("nonexistent");
    assertEquals("nonexistent", response.getPipelineName());
    assertNotNull(response.getRuns());
    assertTrue(response.getRuns().isEmpty());
  }

  @Test
  void getAllRunsReturnsAllRunsOrderedByRunNo() {
    OffsetDateTime now = OffsetDateTime.now();
    savePipelineRun("ordered", 2, RunStatus.FAILED, now, now);
    savePipelineRun("ordered", 1, RunStatus.SUCCESS, now, now);
    savePipelineRun("ordered", 3, RunStatus.SUCCESS, now, now);

    PipelineRunsResponse response =
        reportService.getAllRuns("ordered");
    assertEquals("ordered", response.getPipelineName());
    assertEquals(3, response.getRuns().size());
    assertEquals(1, response.getRuns().get(0).getRunNo());
    assertEquals(2, response.getRuns().get(1).getRunNo());
    assertEquals(3, response.getRuns().get(2).getRunNo());
  }

  @Test
  void getAllRunsMapsStatusCorrectly() {
    OffsetDateTime now = OffsetDateTime.now();
    savePipelineRun("statuspipe", 1, RunStatus.SUCCESS, now, now);
    savePipelineRun("statuspipe", 2, RunStatus.FAILED, now, now);

    PipelineRunsResponse response =
        reportService.getAllRuns("statuspipe");
    assertEquals("SUCCESS",
        response.getRuns().get(0).getStatus());
    assertEquals("FAILED",
        response.getRuns().get(1).getStatus());
  }

  @Test
  void getAllRunsMapsGitFields() {
    PipelineRunEntity run = new PipelineRunEntity();
    run.setPipelineName("gitpipe");
    run.setRunNo(1);
    run.setStatus(RunStatus.SUCCESS);
    run.setGitHash("abc123");
    run.setGitBranch("main");
    run.setGitRepo("git@example.com:org/repo.git");
    run.setStartTime(OffsetDateTime.now());
    run.setEndTime(OffsetDateTime.now());
    pipelineRunRepo.save(run);

    PipelineRunsResponse response =
        reportService.getAllRuns("gitpipe");
    assertEquals(1, response.getRuns().size());
    assertEquals("abc123",
        response.getRuns().get(0).getGitHash());
    assertEquals("main",
        response.getRuns().get(0).getGitBranch());
    assertEquals("git@example.com:org/repo.git",
        response.getRuns().get(0).getGitRepo());
  }

  @Test
  void getAllRunsOnlyReturnsMatchingPipeline() {
    OffsetDateTime now = OffsetDateTime.now();
    savePipelineRun("pipeA", 1, RunStatus.SUCCESS, now, now);
    savePipelineRun("pipeB", 1, RunStatus.SUCCESS, now, now);
    savePipelineRun("pipeB", 2, RunStatus.SUCCESS, now, now);

    PipelineRunsResponse response =
        reportService.getAllRuns("pipeA");
    assertEquals(1, response.getRuns().size());
  }

  @Test
  void getRunThrowsWhenNotFound() {
    assertThrows(ResourceNotFoundException.class,
        () -> reportService.getRun("ghost", 1));
  }

  @Test
  void getRunReturnsRunWithStages() {
    PipelineRunEntity run = buildRunWithStagesAndJobs(
        "multi", 1, RunStatus.SUCCESS);
    pipelineRunRepo.save(run);

    PipelineRunDetailResponse response =
        reportService.getRun("multi", 1);

    assertEquals("multi", response.getPipelineName());
    assertEquals(1, response.getRunNo());
    assertEquals("SUCCESS", response.getStatus());
    assertNotNull(response.getStages());
    assertEquals(2, response.getStages().size());
  }

  @Test
  void getRunStagesDontIncludeJobs() {
    PipelineRunEntity run = buildRunWithStagesAndJobs(
        "nojobs", 1, RunStatus.SUCCESS);
    pipelineRunRepo.save(run);

    PipelineRunDetailResponse response =
        reportService.getRun("nojobs", 1);

    response.getStages().forEach(
        ss -> assertNull(ss.getJobs()));
  }

  @Test
  void getRunMapsGitFieldsToResponse() {
    PipelineRunEntity run = new PipelineRunEntity();
    run.setPipelineName("gitrun");
    run.setRunNo(1);
    run.setStatus(RunStatus.RUNNING);
    run.setGitHash("deadbeef");
    run.setGitBranch("feature-x");
    run.setGitRepo("https://github.com/org/repo");
    run.setStartTime(OffsetDateTime.now());
    pipelineRunRepo.save(run);

    PipelineRunDetailResponse response =
        reportService.getRun("gitrun", 1);
    assertEquals("deadbeef", response.getGitHash());
    assertEquals("feature-x", response.getGitBranch());
    assertEquals("https://github.com/org/repo",
        response.getGitRepo());
  }

  @Test
  void getStageThrowsWhenRunNotFound() {
    assertThrows(ResourceNotFoundException.class,
        () -> reportService.getStage("ghost", 99, "build"));
  }

  @Test
  void getStageThrowsWhenStageNotFound() {
    OffsetDateTime now = OffsetDateTime.now();
    savePipelineRun("hasrun", 1, RunStatus.SUCCESS, now, now);

    assertThrows(ResourceNotFoundException.class,
        () -> reportService.getStage(
            "hasrun", 1, "nosuchstage"));
  }

  @Test
  void getStageReturnsStageWithJobs() {
    PipelineRunEntity run = buildRunWithStagesAndJobs(
        "stagelevel", 1, RunStatus.SUCCESS);
    pipelineRunRepo.save(run);

    PipelineRunDetailResponse response =
        reportService.getStage("stagelevel", 1, "build");

    assertEquals(1, response.getStages().size());
    assertEquals("build",
        response.getStages().get(0).getName());
    assertNotNull(response.getStages().get(0).getJobs());
    assertEquals(1,
        response.getStages().get(0).getJobs().size());
    assertEquals("compile",
        response.getStages().get(0).getJobs().get(0).getName());
  }

  @Test
  void getStageMapsStatusCorrectly() {
    PipelineRunEntity run = buildRunWithStagesAndJobs(
        "stagestat", 1, RunStatus.FAILED);
    pipelineRunRepo.save(run);

    PipelineRunDetailResponse response =
        reportService.getStage("stagestat", 1, "test");

    assertEquals("FAILED",
        response.getStages().get(0).getStatus());
  }

  @Test
  void getJobThrowsWhenRunNotFound() {
    assertThrows(ResourceNotFoundException.class,
        () -> reportService.getJob(
            "ghost", 99, "build", "compile"));
  }

  @Test
  void getJobThrowsWhenStageNotFound() {
    OffsetDateTime now = OffsetDateTime.now();
    savePipelineRun("hasjobrun", 1, RunStatus.SUCCESS, now, now);

    assertThrows(ResourceNotFoundException.class,
        () -> reportService.getJob(
            "hasjobrun", 1, "nosuchstage", "compile"));
  }

  @Test
  void getJobThrowsWhenJobNotFound() {
    PipelineRunEntity run = buildRunWithStagesAndJobs(
        "hasjob", 1, RunStatus.SUCCESS);
    pipelineRunRepo.save(run);

    assertThrows(ResourceNotFoundException.class,
        () -> reportService.getJob(
            "hasjob", 1, "build", "nosuchjob"));
  }

  @Test
  void getJobReturnsCorrectJob() {
    PipelineRunEntity run = buildRunWithStagesAndJobs(
        "joblevel", 1, RunStatus.SUCCESS);
    pipelineRunRepo.save(run);

    PipelineRunDetailResponse response =
        reportService.getJob("joblevel", 1, "build", "compile");

    assertEquals(1, response.getStages().size());
    assertEquals("build",
        response.getStages().get(0).getName());
    assertEquals(1,
        response.getStages().get(0).getJobs().size());
    assertEquals("compile",
        response.getStages().get(0).getJobs().get(0).getName());
  }

  @Test
  void getJobReturnsSingleJobEvenWhenMultipleJobsInStage() {
    PipelineRunEntity run = new PipelineRunEntity();
    run.setPipelineName("multijob");
    run.setRunNo(1);
    run.setStatus(RunStatus.SUCCESS);
    run.setStartTime(OffsetDateTime.now());
    run.setEndTime(OffsetDateTime.now());

    StageRunEntity stage = new StageRunEntity();
    stage.setStageName("build");
    stage.setStageOrder(0);
    stage.setStatus(RunStatus.SUCCESS);
    stage.setStartTime(OffsetDateTime.now());
    stage.setEndTime(OffsetDateTime.now());
    run.addStage(stage);

    JobRunEntity job1 = new JobRunEntity();
    job1.setJobName("compile");
    job1.setStatus(RunStatus.SUCCESS);
    job1.setStartTime(OffsetDateTime.now());
    job1.setEndTime(OffsetDateTime.now());
    stage.addJob(job1);

    JobRunEntity job2 = new JobRunEntity();
    job2.setJobName("lint");
    job2.setStatus(RunStatus.FAILED);
    job2.setStartTime(OffsetDateTime.now());
    job2.setEndTime(OffsetDateTime.now());
    stage.addJob(job2);

    pipelineRunRepo.save(run);

    PipelineRunDetailResponse response =
        reportService.getJob("multijob", 1, "build", "lint");

    assertEquals(1,
        response.getStages().get(0).getJobs().size());
    assertEquals("lint",
        response.getStages().get(0).getJobs().get(0).getName());
    assertEquals("FAILED",
        response.getStages().get(0).getJobs().get(0).getStatus());
  }

  private PipelineRunEntity savePipelineRun(String name, int runNo,
      RunStatus status, OffsetDateTime start, OffsetDateTime end) {
    PipelineRunEntity run = new PipelineRunEntity();
    run.setPipelineName(name);
    run.setRunNo(runNo);
    run.setStatus(status);
    run.setStartTime(start);
    run.setEndTime(end);
    return pipelineRunRepo.save(run);
  }

  private PipelineRunEntity buildRunWithStagesAndJobs(
      String name, int runNo, RunStatus overallStatus) {
    PipelineRunEntity run = new PipelineRunEntity();
    run.setPipelineName(name);
    run.setRunNo(runNo);
    run.setStatus(overallStatus);
    run.setStartTime(OffsetDateTime.now());
    run.setEndTime(OffsetDateTime.now());

    StageRunEntity build = new StageRunEntity();
    build.setStageName("build");
    build.setStageOrder(0);
    build.setStatus(RunStatus.SUCCESS);
    build.setStartTime(OffsetDateTime.now());
    build.setEndTime(OffsetDateTime.now());

    JobRunEntity compile = new JobRunEntity();
    compile.setJobName("compile");
    compile.setStatus(RunStatus.SUCCESS);
    compile.setStartTime(OffsetDateTime.now());
    compile.setEndTime(OffsetDateTime.now());
    build.addJob(compile);
    run.addStage(build);

    StageRunEntity test = new StageRunEntity();
    test.setStageName("test");
    test.setStageOrder(1);
    test.setStatus(overallStatus);
    test.setStartTime(OffsetDateTime.now());
    test.setEndTime(OffsetDateTime.now());

    JobRunEntity unittest = new JobRunEntity();
    unittest.setJobName("unittest");
    unittest.setStatus(overallStatus);
    unittest.setStartTime(OffsetDateTime.now());
    unittest.setEndTime(OffsetDateTime.now());
    test.addJob(unittest);
    run.addStage(test);

    return run;
  }
}

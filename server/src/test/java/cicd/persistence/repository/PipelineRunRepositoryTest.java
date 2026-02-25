package cicd.persistence.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cicd.persistence.entity.JobRunEntity;
import cicd.persistence.entity.PipelineRunEntity;
import cicd.persistence.entity.RunStatus;
import cicd.persistence.entity.StageRunEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/** Tests for the pipeline run persistence layer using H2 in-memory DB. */
@DataJpaTest
@ActiveProfiles("test")
@EnableAutoConfiguration
@ContextConfiguration(classes = {
    PipelineRunRepositoryTest.class,
    cicd.persistence.config.JpaConfig.class
})
class PipelineRunRepositoryTest {

  @Autowired
  private PipelineRunRepository pipelineRunRepo;

  @Autowired
  private StageRunRepository stageRunRepo;

  @Autowired
  private JobRunRepository jobRunRepo;

  @Test
  void nextRunNoStartsAtOne() {
    int runNo = pipelineRunRepo.nextRunNo("myPipeline");
    assertEquals(1, runNo);
  }

  @Test
  void nextRunNoIncrements() {
    savePipelineRun("myPipeline", 1, RunStatus.SUCCESS);
    savePipelineRun("myPipeline", 2, RunStatus.FAILED);

    int runNo = pipelineRunRepo.nextRunNo("myPipeline");
    assertEquals(3, runNo);
  }

  @Test
  void nextRunNoIsPerPipeline() {
    savePipelineRun("alpha", 1, RunStatus.SUCCESS);
    savePipelineRun("beta", 1, RunStatus.SUCCESS);
    savePipelineRun("beta", 2, RunStatus.SUCCESS);

    assertEquals(2, pipelineRunRepo.nextRunNo("alpha"));
    assertEquals(3, pipelineRunRepo.nextRunNo("beta"));
  }

  @Test
  void findByPipelineNameOrderedByRunNo() {
    savePipelineRun("default", 2, RunStatus.FAILED);
    savePipelineRun("default", 1, RunStatus.SUCCESS);
    savePipelineRun("other", 1, RunStatus.SUCCESS);

    List<PipelineRunEntity> runs =
        pipelineRunRepo.findByPipelineNameOrderByRunNoAsc("default");

    assertEquals(2, runs.size());
    assertEquals(1, runs.get(0).getRunNo());
    assertEquals(2, runs.get(1).getRunNo());
  }

  @Test
  void findByPipelineNameAndRunNo() {
    savePipelineRun("default", 1, RunStatus.SUCCESS);
    savePipelineRun("default", 2, RunStatus.FAILED);

    Optional<PipelineRunEntity> found =
        pipelineRunRepo.findByPipelineNameAndRunNo("default", 2);

    assertTrue(found.isPresent());
    assertEquals(RunStatus.FAILED, found.get().getStatus());
  }

  @Test
  void cascadeSavesStagesAndJobs() {
    PipelineRunEntity pr = new PipelineRunEntity();
    pr.setPipelineName("cascade");
    pr.setRunNo(1);
    pr.setStatus(RunStatus.SUCCESS);
    pr.setStartTime(OffsetDateTime.now());
    pr.setEndTime(OffsetDateTime.now());
    pr.setGitHash("abc123");
    pr.setGitBranch("main");
    pr.setGitRepo("git@example.com:repo.git");

    StageRunEntity stage = new StageRunEntity();
    stage.setStageName("build");
    stage.setStageOrder(0);
    stage.setStatus(RunStatus.SUCCESS);
    stage.setStartTime(OffsetDateTime.now());
    stage.setEndTime(OffsetDateTime.now());
    pr.addStage(stage);

    JobRunEntity job = new JobRunEntity();
    job.setJobName("compile");
    job.setStatus(RunStatus.SUCCESS);
    job.setStartTime(OffsetDateTime.now());
    job.setEndTime(OffsetDateTime.now());
    stage.addJob(job);

    pipelineRunRepo.save(pr);

    List<StageRunEntity> stages =
        stageRunRepo.findByPipelineRunIdOrderByStageOrderAsc(
            pr.getId());
    assertEquals(1, stages.size());
    assertEquals("build", stages.get(0).getStageName());

    List<JobRunEntity> jobs =
        jobRunRepo.findByStageRunId(stages.get(0).getId());
    assertEquals(1, jobs.size());
    assertEquals("compile", jobs.get(0).getJobName());
  }

  @Test
  void findStageByName() {
    PipelineRunEntity pr = new PipelineRunEntity();
    pr.setPipelineName("stageTest");
    pr.setRunNo(1);
    pr.setStatus(RunStatus.SUCCESS);

    StageRunEntity build = new StageRunEntity();
    build.setStageName("build");
    build.setStageOrder(0);
    build.setStatus(RunStatus.SUCCESS);
    pr.addStage(build);

    StageRunEntity test = new StageRunEntity();
    test.setStageName("test");
    test.setStageOrder(1);
    test.setStatus(RunStatus.FAILED);
    pr.addStage(test);

    pipelineRunRepo.save(pr);

    Optional<StageRunEntity> found =
        stageRunRepo.findByPipelineRunIdAndStageName(
            pr.getId(), "test");

    assertTrue(found.isPresent());
    assertEquals(RunStatus.FAILED, found.get().getStatus());
  }

  @Test
  void findJobByName() {
    PipelineRunEntity pr = new PipelineRunEntity();
    pr.setPipelineName("jobTest");
    pr.setRunNo(1);
    pr.setStatus(RunStatus.SUCCESS);

    StageRunEntity stage = new StageRunEntity();
    stage.setStageName("build");
    stage.setStageOrder(0);
    stage.setStatus(RunStatus.SUCCESS);
    pr.addStage(stage);

    JobRunEntity compile = new JobRunEntity();
    compile.setJobName("compile");
    compile.setStatus(RunStatus.SUCCESS);
    stage.addJob(compile);

    JobRunEntity lint = new JobRunEntity();
    lint.setJobName("lint");
    lint.setStatus(RunStatus.FAILED);
    stage.addJob(lint);

    pipelineRunRepo.save(pr);

    Optional<JobRunEntity> found =
        jobRunRepo.findByStageRunIdAndJobName(
            stage.getId(), "lint");

    assertTrue(found.isPresent());
    assertEquals(RunStatus.FAILED, found.get().getStatus());
  }

  @Test
  void pendingStatusCanBePersisted() {
    PipelineRunEntity pr = savePipelineRun("pending-test", 1, RunStatus.PENDING);

    Optional<PipelineRunEntity> found =
        pipelineRunRepo.findByPipelineNameAndRunNo("pending-test", 1);

    assertTrue(found.isPresent());
    assertEquals(RunStatus.PENDING, found.get().getStatus());
  }

  @Test
  void pendingStatusTransitionsToRunning() {
    PipelineRunEntity pr = savePipelineRun("transition-test", 1, RunStatus.PENDING);

    pr.setStatus(RunStatus.RUNNING);
    pr.setStartTime(OffsetDateTime.now());
    pipelineRunRepo.save(pr);

    Optional<PipelineRunEntity> found =
        pipelineRunRepo.findByPipelineNameAndRunNo("transition-test", 1);
    assertTrue(found.isPresent());
    assertEquals(RunStatus.RUNNING, found.get().getStatus());
  }

  private PipelineRunEntity savePipelineRun(
      String name, int runNo, RunStatus status) {
    PipelineRunEntity pr = new PipelineRunEntity();
    pr.setPipelineName(name);
    pr.setRunNo(runNo);
    pr.setStatus(status);
    pr.setStartTime(OffsetDateTime.now());
    pr.setEndTime(OffsetDateTime.now());
    return pipelineRunRepo.save(pr);
  }
}

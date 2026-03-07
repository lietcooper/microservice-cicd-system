package cicd.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cicd.docker.DockerRunner;
import cicd.executor.JobResult;
import cicd.model.Job;
import cicd.model.Pipeline;
import cicd.persistence.entity.JobRunEntity;
import cicd.persistence.entity.PipelineRunEntity;
import cicd.persistence.entity.RunStatus;
import cicd.persistence.entity.StageRunEntity;
import cicd.persistence.repository.JobRunRepository;
import cicd.persistence.repository.PipelineRunRepository;
import cicd.persistence.repository.StageRunRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Tests for PipelineExecutorService (legacy synchronous executor).
 * Uses Mockito mocks for DockerRunner and all three repositories.
 * NOTE: This class is not a Spring bean (kept for reference), so
 * pure unit tests with mocks are appropriate here.
 */
class PipelineExecutorServiceTest {

  private DockerRunner dockerRunner;
  private PipelineRunRepository pipelineRunRepo;
  private StageRunRepository stageRunRepo;
  private JobRunRepository jobRunRepo;
  private PipelineExecutorService service;

  @BeforeEach
  void setUp() {
    dockerRunner = mock(DockerRunner.class);
    pipelineRunRepo = mock(PipelineRunRepository.class);
    stageRunRepo = mock(StageRunRepository.class);
    jobRunRepo = mock(JobRunRepository.class);

    service = new PipelineExecutorService(
        dockerRunner, pipelineRunRepo, stageRunRepo, jobRunRepo);

    // Default: nextRunNo returns 1
    when(pipelineRunRepo.nextRunNo(anyString())).thenReturn(1);

    // Default: save returns a copy with the entity itself (Mockito returns null unless stubbed)
    when(pipelineRunRepo.save(any(PipelineRunEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(stageRunRepo.save(any(StageRunEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(jobRunRepo.save(any(JobRunEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
  }

  // ── Happy-path tests ─────────────────────────────────────────────────────────

  @Test
  void executePipelineWithSingleSuccessfulJobReturnsPipelineRun() {
    Pipeline pipeline = buildPipeline("build", "compile", "alpine", "echo ok");
    when(dockerRunner.runJob(anyString(), any(), anyString()))
        .thenReturn(new JobResult("compile", 0, "ok"));

    PipelineRunEntity result = service.executePipeline(
        pipeline, "/repo", "main", "abc123");

    assertNotNull(result);
    assertEquals("default", result.getPipelineName());
    assertEquals(RunStatus.SUCCESS, result.getStatus());
  }

  @Test
  void executePipelineSetsGitHashFromCommitArg() {
    Pipeline pipeline = buildPipeline("build", "compile", "alpine", "echo ok");
    when(dockerRunner.runJob(anyString(), any(), anyString()))
        .thenReturn(new JobResult("compile", 0, ""));

    PipelineRunEntity result = service.executePipeline(
        pipeline, "/repo", "main", "deadbeef");

    assertEquals("deadbeef", result.getGitHash());
  }

  @Test
  void executePipelineSetsGitBranchFromBranchArg() {
    Pipeline pipeline = buildPipeline("build", "compile", "alpine", "echo ok");
    when(dockerRunner.runJob(anyString(), any(), anyString()))
        .thenReturn(new JobResult("compile", 0, ""));

    PipelineRunEntity result = service.executePipeline(
        pipeline, "/repo", "feature-x", null);

    assertEquals("feature-x", result.getGitBranch());
  }

  @Test
  void executePipelineSetsRepoPath() {
    Pipeline pipeline = buildPipeline("build", "compile", "alpine", "echo ok");
    when(dockerRunner.runJob(anyString(), any(), anyString()))
        .thenReturn(new JobResult("compile", 0, ""));

    PipelineRunEntity result = service.executePipeline(
        pipeline, "/my/repo", "main", "abc");

    assertEquals("/my/repo", result.getGitRepo());
  }

  @Test
  void executePipelineSavesAndFlushesAllEntities() {
    Pipeline pipeline = buildPipeline("build", "compile", "alpine", "echo ok");
    when(dockerRunner.runJob(anyString(), any(), anyString()))
        .thenReturn(new JobResult("compile", 0, ""));

    service.executePipeline(pipeline, "/repo", "main", "abc");

    // Pipeline record saved twice (initial + end update)
    verify(pipelineRunRepo, times(2)).save(any(PipelineRunEntity.class));
    verify(pipelineRunRepo, atLeastOnce()).flush();
    // Stage record saved twice (initial + end update)
    verify(stageRunRepo, times(2)).save(any(StageRunEntity.class));
    verify(stageRunRepo, atLeastOnce()).flush();
    // Job record saved twice (initial + end update)
    verify(jobRunRepo, times(2)).save(any(JobRunEntity.class));
    verify(jobRunRepo, atLeastOnce()).flush();
  }

  @Test
  void executePipelineWithFailingJobSetsPipelineToFailed() {
    Pipeline pipeline = buildPipeline("build", "compile", "alpine", "echo fail");
    when(dockerRunner.runJob(anyString(), any(), anyString()))
        .thenReturn(new JobResult("compile", 1, "Error"));

    PipelineRunEntity result = service.executePipeline(
        pipeline, "/repo", "main", "abc");

    assertEquals(RunStatus.FAILED, result.getStatus());
  }

  @Test
  void executePipelineWithFailingJobSetsStageToFailed() {
    Pipeline pipeline = buildPipeline("test", "unittest", "alpine", "fail");
    when(dockerRunner.runJob(anyString(), any(), anyString()))
        .thenReturn(new JobResult("unittest", 2, "test failure"));

    service.executePipeline(pipeline, "/repo", "main", "abc");

    ArgumentCaptor<StageRunEntity> stageCaptor =
        ArgumentCaptor.forClass(StageRunEntity.class);
    verify(stageRunRepo, times(2)).save(stageCaptor.capture());

    // The last saved stage entity should have FAILED status
    List<StageRunEntity> savedStages = stageCaptor.getAllValues();
    StageRunEntity finalStage = savedStages.get(savedStages.size() - 1);
    assertEquals(RunStatus.FAILED, finalStage.getStatus());
  }

  @Test
  void executePipelineWithFailingJobStopsRemainingJobs() {
    // Two jobs in same stage; first fails; second should not be executed
    Pipeline pipeline = new Pipeline();
    pipeline.name = "default";
    pipeline.stages = List.of("build");

    Job job1 = new Job();
    job1.name = "job1";
    job1.stage = "build";
    job1.image = "alpine";
    job1.script = List.of("echo fail");
    pipeline.jobs.put("job1", job1);

    Job job2 = new Job();
    job2.name = "job2";
    job2.stage = "build";
    job2.image = "alpine";
    job2.script = List.of("echo ok");
    pipeline.jobs.put("job2", job2);

    // Only first call fails; second would return success
    when(dockerRunner.runJob(anyString(), any(), anyString()))
        .thenReturn(new JobResult("job1", 1, "fail"))
        .thenReturn(new JobResult("job2", 0, "ok"));

    service.executePipeline(pipeline, "/repo", "main", "abc");

    // Docker should only be called once because job1 fails and we stop
    verify(dockerRunner, times(1)).runJob(anyString(), any(), anyString());
  }

  @Test
  void executePipelineStopsNextStageWhenFirstStageFails() {
    // Two stages; first stage's job fails; second stage should not execute
    Pipeline pipeline = new Pipeline();
    pipeline.name = "default";
    pipeline.stages = List.of("build", "test");

    Job buildJob = new Job();
    buildJob.name = "compile";
    buildJob.stage = "build";
    buildJob.image = "alpine";
    buildJob.script = List.of("fail");
    pipeline.jobs.put("compile", buildJob);

    Job testJob = new Job();
    testJob.name = "unittest";
    testJob.stage = "test";
    testJob.image = "alpine";
    testJob.script = List.of("ok");
    pipeline.jobs.put("unittest", testJob);

    when(dockerRunner.runJob(anyString(), any(), anyString()))
        .thenReturn(new JobResult("compile", 1, "fail"));

    service.executePipeline(pipeline, "/repo", "main", "abc");

    // Only the build stage job should be attempted
    verify(dockerRunner, times(1)).runJob(anyString(), any(), anyString());
  }

  @Test
  void executePipelineSetsRunNoFromRepository() {
    when(pipelineRunRepo.nextRunNo("default")).thenReturn(5);
    Pipeline pipeline = buildPipeline("build", "compile", "alpine", "echo ok");
    when(dockerRunner.runJob(anyString(), any(), anyString()))
        .thenReturn(new JobResult("compile", 0, ""));

    PipelineRunEntity result = service.executePipeline(
        pipeline, "/repo", "main", "abc");

    assertEquals(5, result.getRunNo());
  }

  @Test
  void executePipelineWithJobOutputDoesNotThrow() {
    Pipeline pipeline = buildPipeline("build", "compile", "alpine", "echo hello");
    when(dockerRunner.runJob(anyString(), any(), anyString()))
        .thenReturn(new JobResult("compile", 0, "hello\nworld\n"));

    PipelineRunEntity result = service.executePipeline(
        pipeline, "/repo", "main", "abc");

    assertNotNull(result);
    assertEquals(RunStatus.SUCCESS, result.getStatus());
  }

  @Test
  void executePipelineWithNullOutputDoesNotThrow() {
    Pipeline pipeline = buildPipeline("build", "compile", "alpine", "echo");
    when(dockerRunner.runJob(anyString(), any(), anyString()))
        .thenReturn(new JobResult("compile", 0, null));

    PipelineRunEntity result = service.executePipeline(
        pipeline, "/repo", "main", "abc");

    assertNotNull(result);
    assertEquals(RunStatus.SUCCESS, result.getStatus());
  }

  @Test
  void executePipelineWithMultipleSuccessfulStagesSucceeds() {
    Pipeline pipeline = new Pipeline();
    pipeline.name = "default";
    pipeline.stages = List.of("build", "test");

    Job buildJob = new Job();
    buildJob.name = "compile";
    buildJob.stage = "build";
    buildJob.image = "alpine";
    buildJob.script = List.of("echo build");
    pipeline.jobs.put("compile", buildJob);

    Job testJob = new Job();
    testJob.name = "unittest";
    testJob.stage = "test";
    testJob.image = "alpine";
    testJob.script = List.of("echo test");
    pipeline.jobs.put("unittest", testJob);

    when(dockerRunner.runJob(anyString(), any(), anyString()))
        .thenReturn(new JobResult("compile", 0, ""))
        .thenReturn(new JobResult("unittest", 0, ""));

    PipelineRunEntity result = service.executePipeline(
        pipeline, "/repo", "main", "abc");

    assertEquals(RunStatus.SUCCESS, result.getStatus());
    // Both jobs should have been executed
    verify(dockerRunner, times(2)).runJob(anyString(), any(), anyString());
  }

  @Test
  void executePipelineJobRunEntityHasCorrectJobName() {
    Pipeline pipeline = buildPipeline("build", "compile", "alpine", "echo ok");
    when(dockerRunner.runJob(anyString(), any(), anyString()))
        .thenReturn(new JobResult("compile", 0, ""));

    service.executePipeline(pipeline, "/repo", "main", "abc");

    ArgumentCaptor<JobRunEntity> jobCaptor =
        ArgumentCaptor.forClass(JobRunEntity.class);
    verify(jobRunRepo, times(2)).save(jobCaptor.capture());

    // First saved entity should have the job name set
    assertEquals("compile", jobCaptor.getAllValues().get(0).getJobName());
  }

  @Test
  void executePipelineJobRunEntityIsSavedTwicePerJob() {
    // Job is saved with RUNNING status, then updated with SUCCESS/FAILED
    // Both saves go through the same entity object (mutated in place)
    Pipeline pipeline = buildPipeline("build", "compile", "alpine", "echo ok");
    when(dockerRunner.runJob(anyString(), any(), anyString()))
        .thenReturn(new JobResult("compile", 0, ""));

    service.executePipeline(pipeline, "/repo", "main", "abc");

    // Should be saved twice: initial create and end update
    verify(jobRunRepo, times(2)).save(any(JobRunEntity.class));
  }

  @Test
  void executePipelineJobRunEntityEndsWithSuccessStatus() {
    Pipeline pipeline = buildPipeline("build", "compile", "alpine", "echo ok");
    when(dockerRunner.runJob(anyString(), any(), anyString()))
        .thenReturn(new JobResult("compile", 0, ""));

    service.executePipeline(pipeline, "/repo", "main", "abc");

    ArgumentCaptor<JobRunEntity> jobCaptor =
        ArgumentCaptor.forClass(JobRunEntity.class);
    verify(jobRunRepo, times(2)).save(jobCaptor.capture());

    // The final state of the captured entity is SUCCESS
    // (both captures point to the same mutated object)
    assertEquals(RunStatus.SUCCESS, jobCaptor.getValue().getStatus());
  }

  @Test
  void executePipelinePipelineRunEntityIsSavedTwice() {
    // Pipeline entity is saved initially (RUNNING) then updated at completion
    // Both saves use the same entity object (mutated in place)
    Pipeline pipeline = buildPipeline("build", "compile", "alpine", "echo ok");
    when(dockerRunner.runJob(anyString(), any(), anyString()))
        .thenReturn(new JobResult("compile", 0, ""));

    service.executePipeline(pipeline, "/repo", "main", "abc");

    // Should be saved twice total
    verify(pipelineRunRepo, times(2)).save(any(PipelineRunEntity.class));
  }

  @Test
  void executePipelineDoesNotCallDockerWhenNoJobs() {
    Pipeline pipeline = new Pipeline();
    pipeline.name = "empty";
    pipeline.stages = List.of();

    service.executePipeline(pipeline, "/repo", "main", "abc");

    verify(dockerRunner, never()).runJob(anyString(), any(), anyString());
  }

  // ── Helper ──────────────────────────────────────────────────────────────────

  private Pipeline buildPipeline(
      String stageName, String jobName, String image, String script) {
    Pipeline pipeline = new Pipeline();
    pipeline.name = "default";
    pipeline.stages = List.of(stageName);

    Job job = new Job();
    job.name = jobName;
    job.stage = stageName;
    job.image = image;
    job.script = List.of(script);
    pipeline.jobs.put(jobName, job);

    return pipeline;
  }
}

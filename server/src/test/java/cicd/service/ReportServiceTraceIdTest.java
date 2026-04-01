package cicd.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import cicd.api.dto.PipelineRunDetailResponse;
import cicd.api.dto.PipelineRunsResponse;
import cicd.persistence.entity.JobRunEntity;
import cicd.persistence.entity.PipelineRunEntity;
import cicd.persistence.entity.RunStatus;
import cicd.persistence.entity.StageRunEntity;
import cicd.persistence.repository.PipelineRunRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * Tests that ReportService correctly populates traceId
 * from PipelineRunEntity into response DTOs.
 */
@DataJpaTest
@ActiveProfiles("test")
@EnableAutoConfiguration
@ContextConfiguration(classes = {
    ReportServiceTraceIdTest.class,
    cicd.persistence.config.JpaConfig.class
})
@Import(ReportService.class)
class ReportServiceTraceIdTest {

  @Autowired
  private ReportService reportService;

  @Autowired
  private PipelineRunRepository pipelineRunRepo;

  @Test
  void getAllRunsIncludesTraceId() {
    PipelineRunEntity run = createRun("traced", 1, "abc123def456");
    pipelineRunRepo.save(run);

    PipelineRunsResponse response =
        reportService.getAllRuns("traced");

    assertEquals(1, response.getRuns().size());
    assertEquals("abc123def456",
        response.getRuns().get(0).getTraceId());
  }

  @Test
  void getAllRunsReturnsNullTraceIdWhenNotSet() {
    PipelineRunEntity run = createRun("noTrace", 1, null);
    pipelineRunRepo.save(run);

    PipelineRunsResponse response =
        reportService.getAllRuns("noTrace");

    assertEquals(1, response.getRuns().size());
    assertNull(response.getRuns().get(0).getTraceId());
  }

  @Test
  void getRunIncludesTraceId() {
    PipelineRunEntity run = createRun("runTrace", 1,
        "0af7651916cd43dd8448eb211c80319c");
    pipelineRunRepo.save(run);

    PipelineRunDetailResponse response =
        reportService.getRun("runTrace", 1);

    assertEquals("0af7651916cd43dd8448eb211c80319c",
        response.getTraceId());
  }

  @Test
  void getStageIncludesTraceId() {
    PipelineRunEntity run = createRunWithStage("stageTrace", 1,
        "aabbccdd11223344");
    pipelineRunRepo.save(run);

    PipelineRunDetailResponse response =
        reportService.getStage("stageTrace", 1, "build");

    assertEquals("aabbccdd11223344", response.getTraceId());
  }

  @Test
  void getJobIncludesTraceId() {
    PipelineRunEntity run = createRunWithStageAndJob(
        "jobTrace", 1, "deadbeef12345678");
    pipelineRunRepo.save(run);

    PipelineRunDetailResponse response =
        reportService.getJob("jobTrace", 1, "build", "compile");

    assertEquals("deadbeef12345678", response.getTraceId());
  }

  private PipelineRunEntity createRun(String name, int runNo,
      String traceId) {
    PipelineRunEntity run = new PipelineRunEntity();
    run.setPipelineName(name);
    run.setRunNo(runNo);
    run.setStatus(RunStatus.SUCCESS);
    run.setStartTime(OffsetDateTime.now());
    run.setEndTime(OffsetDateTime.now());
    run.setTraceId(traceId);
    return run;
  }

  private PipelineRunEntity createRunWithStage(String name,
      int runNo, String traceId) {
    PipelineRunEntity run = createRun(name, runNo, traceId);

    StageRunEntity stage = new StageRunEntity();
    stage.setStageName("build");
    stage.setStageOrder(0);
    stage.setStatus(RunStatus.SUCCESS);
    stage.setStartTime(OffsetDateTime.now());
    stage.setEndTime(OffsetDateTime.now());
    run.addStage(stage);

    return run;
  }

  private PipelineRunEntity createRunWithStageAndJob(String name,
      int runNo, String traceId) {
    PipelineRunEntity run = createRunWithStage(name, runNo, traceId);

    StageRunEntity stage = run.getStageRuns().get(0);
    JobRunEntity job = new JobRunEntity();
    job.setJobName("compile");
    job.setStatus(RunStatus.SUCCESS);
    job.setStartTime(OffsetDateTime.now());
    job.setEndTime(OffsetDateTime.now());
    stage.addJob(job);

    return run;
  }
}

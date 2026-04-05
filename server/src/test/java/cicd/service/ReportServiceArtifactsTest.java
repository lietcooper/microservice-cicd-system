package cicd.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import cicd.api.dto.PipelineRunDetailResponse;
import cicd.persistence.entity.ArtifactEntity;
import cicd.persistence.entity.JobRunEntity;
import cicd.persistence.entity.PipelineRunEntity;
import cicd.persistence.entity.RunStatus;
import cicd.persistence.entity.StageRunEntity;
import cicd.persistence.repository.ArtifactRepository;
import cicd.persistence.repository.JobRunRepository;
import cicd.persistence.repository.PipelineRunRepository;
import cicd.persistence.repository.StageRunRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/** Tests that artifacts are included in report DTOs. */
@DataJpaTest
@ActiveProfiles("test")
@EnableAutoConfiguration
@ContextConfiguration(classes = {
    ReportServiceArtifactsTest.class,
    cicd.persistence.config.JpaConfig.class
})
@Import(ReportService.class)
class ReportServiceArtifactsTest {

  @Autowired
  private ReportService reportService;

  @Autowired
  private PipelineRunRepository pipelineRunRepo;

  @Autowired
  private StageRunRepository stageRunRepo;

  @Autowired
  private JobRunRepository jobRunRepo;

  @Autowired
  private ArtifactRepository artifactRepo;

  @Test
  void getJobIncludesArtifacts() {
    // Build entities
    PipelineRunEntity run = new PipelineRunEntity();
    run.setPipelineName("artpipe");
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

    JobRunEntity job = new JobRunEntity();
    job.setJobName("compile");
    job.setStatus(RunStatus.SUCCESS);
    job.setStartTime(OffsetDateTime.now());
    job.setEndTime(OffsetDateTime.now());
    stage.addJob(job);

    pipelineRunRepo.save(run);

    // Add artifacts via the entity relationship
    ArtifactEntity art1 = new ArtifactEntity();
    art1.setJobRun(job);
    art1.setPattern("build/libs/*.jar");
    art1.setStoragePath("/tmp/cicd-artifacts/artpipe/1/build/compile/build/libs/app.jar");
    job.getArtifacts().add(art1);

    ArtifactEntity art2 = new ArtifactEntity();
    art2.setJobRun(job);
    art2.setPattern("build/reports/*.html");
    art2.setStoragePath("/tmp/cicd-artifacts/artpipe/1/build/compile/build/reports/test.html");
    job.getArtifacts().add(art2);

    jobRunRepo.save(job);
    jobRunRepo.flush();

    // Query and verify
    PipelineRunDetailResponse response =
        reportService.getJob("artpipe", 1, "build", "compile");

    assertNotNull(response.getStages().get(0).getJobs().get(0)
        .getArtifacts());
    assertEquals(2, response.getStages().get(0).getJobs().get(0)
        .getArtifacts().size());
    assertEquals("build/libs/*.jar",
        response.getStages().get(0).getJobs().get(0)
            .getArtifacts().get(0).getPattern());
    assertNotNull(response.getStages().get(0).getJobs().get(0)
        .getArtifacts().get(0).getLocation());
  }

  @Test
  void getJobWithNoArtifacts() {
    PipelineRunEntity run = new PipelineRunEntity();
    run.setPipelineName("noartpipe");
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

    JobRunEntity job = new JobRunEntity();
    job.setJobName("compile");
    job.setStatus(RunStatus.SUCCESS);
    job.setStartTime(OffsetDateTime.now());
    job.setEndTime(OffsetDateTime.now());
    stage.addJob(job);

    pipelineRunRepo.save(run);

    PipelineRunDetailResponse response =
        reportService.getJob("noartpipe", 1, "build", "compile");

    assertNull(response.getStages().get(0).getJobs().get(0)
        .getArtifacts());
  }
}

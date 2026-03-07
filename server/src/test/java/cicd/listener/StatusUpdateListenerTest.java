package cicd.listener;

import static org.junit.jupiter.api.Assertions.*;

import cicd.messaging.StatusUpdateMessage;
import cicd.persistence.entity.JobRunEntity;
import cicd.persistence.entity.PipelineRunEntity;
import cicd.persistence.entity.RunStatus;
import cicd.persistence.entity.StageRunEntity;
import cicd.persistence.repository.JobRunRepository;
import cicd.persistence.repository.PipelineRunRepository;
import cicd.persistence.repository.StageRunRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * Tests for StatusUpdateListener's database write logic.
 * Uses H2 in-memory DB; no RabbitMQ required.
 */
@DataJpaTest
@ActiveProfiles("test")
@EnableAutoConfiguration
@ContextConfiguration(classes = {
    StatusUpdateListenerTest.class,
    cicd.persistence.config.JpaConfig.class
})
@Import(StatusUpdateListener.class)
class StatusUpdateListenerTest {

  @Autowired
  private StatusUpdateListener listener;

  @Autowired
  private PipelineRunRepository pipelineRunRepo;

  @Autowired
  private StageRunRepository stageRunRepo;

  @Autowired
  private JobRunRepository jobRunRepo;

  private PipelineRunEntity savedPipeline;

  @BeforeEach
  void setUp() {
    savedPipeline = new PipelineRunEntity();
    savedPipeline.setPipelineName("test-pipe");
    savedPipeline.setRunNo(1);
    savedPipeline.setStatus(RunStatus.PENDING);
    savedPipeline.setStartTime(OffsetDateTime.now());
    savedPipeline = pipelineRunRepo.save(savedPipeline);
    pipelineRunRepo.flush();
  }

  // ── Pipeline entity updates ──────────────────────────────────────────────────

  @Test
  void handlePipelineUpdatesStatusToRunning() {
    StatusUpdateMessage msg = new StatusUpdateMessage();
    msg.setEntityType("PIPELINE");
    msg.setPipelineRunId(savedPipeline.getId());
    msg.setPipelineName("test-pipe");
    msg.setRunNo(1);
    msg.setStatus("RUNNING");
    msg.setStartTime(OffsetDateTime.now());

    listener.onStatusUpdate(msg);

    Optional<PipelineRunEntity> found =
        pipelineRunRepo.findById(savedPipeline.getId());
    assertTrue(found.isPresent());
    assertEquals(RunStatus.RUNNING, found.get().getStatus());
  }

  @Test
  void handlePipelineUpdatesStatusToSuccess() {
    StatusUpdateMessage msg = new StatusUpdateMessage();
    msg.setEntityType("PIPELINE");
    msg.setPipelineRunId(savedPipeline.getId());
    msg.setStatus("SUCCESS");
    msg.setEndTime(OffsetDateTime.now());

    listener.onStatusUpdate(msg);

    Optional<PipelineRunEntity> found =
        pipelineRunRepo.findById(savedPipeline.getId());
    assertTrue(found.isPresent());
    assertEquals(RunStatus.SUCCESS, found.get().getStatus());
    assertNotNull(found.get().getEndTime());
  }

  @Test
  void handlePipelineUpdatesStatusToFailed() {
    StatusUpdateMessage msg = new StatusUpdateMessage();
    msg.setEntityType("PIPELINE");
    msg.setPipelineRunId(savedPipeline.getId());
    msg.setStatus("FAILED");
    msg.setEndTime(OffsetDateTime.now());

    listener.onStatusUpdate(msg);

    Optional<PipelineRunEntity> found =
        pipelineRunRepo.findById(savedPipeline.getId());
    assertTrue(found.isPresent());
    assertEquals(RunStatus.FAILED, found.get().getStatus());
  }

  @Test
  void handlePipelineForNonExistentRunDoesNotThrow() {
    StatusUpdateMessage msg = new StatusUpdateMessage();
    msg.setEntityType("PIPELINE");
    msg.setPipelineRunId(99999L);
    msg.setStatus("RUNNING");

    // Should not throw; just logs a warning
    assertDoesNotThrow(() -> listener.onStatusUpdate(msg));
  }

  @Test
  void handlePipelineDoesNotSetStartTimeIfNull() {
    savedPipeline.setStartTime(null);
    pipelineRunRepo.save(savedPipeline);

    StatusUpdateMessage msg = new StatusUpdateMessage();
    msg.setEntityType("PIPELINE");
    msg.setPipelineRunId(savedPipeline.getId());
    msg.setStatus("RUNNING");
    msg.setStartTime(null); // null start time in message

    listener.onStatusUpdate(msg);

    Optional<PipelineRunEntity> found =
        pipelineRunRepo.findById(savedPipeline.getId());
    assertTrue(found.isPresent());
    // startTime should still be null since message had null
    assertNull(found.get().getStartTime());
  }

  // ── Stage entity creates/updates ─────────────────────────────────────────────

  @Test
  void handleStageCreatesNewStageRecord() {
    StatusUpdateMessage msg = new StatusUpdateMessage();
    msg.setEntityType("STAGE");
    msg.setPipelineRunId(savedPipeline.getId());
    msg.setStageName("build");
    msg.setStageOrder(0);
    msg.setStatus("RUNNING");
    msg.setStartTime(OffsetDateTime.now());

    listener.onStatusUpdate(msg);

    Optional<StageRunEntity> found =
        stageRunRepo.findByPipelineRunIdAndStageName(
            savedPipeline.getId(), "build");
    assertTrue(found.isPresent());
    assertEquals(RunStatus.RUNNING, found.get().getStatus());
    assertEquals(0, found.get().getStageOrder());
  }

  @Test
  void handleStageUpdatesExistingStageRecord() {
    // Create stage first
    StageRunEntity stage = new StageRunEntity();
    stage.setPipelineRun(savedPipeline);
    stage.setStageName("build");
    stage.setStageOrder(0);
    stage.setStatus(RunStatus.RUNNING);
    stage.setStartTime(OffsetDateTime.now());
    stageRunRepo.save(stage);
    stageRunRepo.flush();

    // Now update to SUCCESS
    StatusUpdateMessage msg = new StatusUpdateMessage();
    msg.setEntityType("STAGE");
    msg.setPipelineRunId(savedPipeline.getId());
    msg.setStageName("build");
    msg.setStatus("SUCCESS");
    msg.setEndTime(OffsetDateTime.now());

    listener.onStatusUpdate(msg);

    Optional<StageRunEntity> found =
        stageRunRepo.findByPipelineRunIdAndStageName(
            savedPipeline.getId(), "build");
    assertTrue(found.isPresent());
    assertEquals(RunStatus.SUCCESS, found.get().getStatus());
    assertNotNull(found.get().getEndTime());
  }

  @Test
  void handleStageForNonExistentPipelineRunDoesNotThrow() {
    StatusUpdateMessage msg = new StatusUpdateMessage();
    msg.setEntityType("STAGE");
    msg.setPipelineRunId(99999L);
    msg.setStageName("build");
    msg.setStatus("RUNNING");

    assertDoesNotThrow(() -> listener.onStatusUpdate(msg));
  }

  @Test
  void handleStageUsesDefaultOrderWhenNull() {
    StatusUpdateMessage msg = new StatusUpdateMessage();
    msg.setEntityType("STAGE");
    msg.setPipelineRunId(savedPipeline.getId());
    msg.setStageName("test");
    msg.setStageOrder(null); // null stage order
    msg.setStatus("RUNNING");
    msg.setStartTime(OffsetDateTime.now());

    listener.onStatusUpdate(msg);

    Optional<StageRunEntity> found =
        stageRunRepo.findByPipelineRunIdAndStageName(
            savedPipeline.getId(), "test");
    assertTrue(found.isPresent());
    assertEquals(0, found.get().getStageOrder()); // defaults to 0
  }

  // ── Job entity creates/updates ───────────────────────────────────────────────

  @Test
  void handleJobCreatesNewJobRecord() {
    // First create stage
    StageRunEntity stage = new StageRunEntity();
    stage.setPipelineRun(savedPipeline);
    stage.setStageName("build");
    stage.setStageOrder(0);
    stage.setStatus(RunStatus.RUNNING);
    stage.setStartTime(OffsetDateTime.now());
    stageRunRepo.save(stage);
    stageRunRepo.flush();

    StatusUpdateMessage msg = new StatusUpdateMessage();
    msg.setEntityType("JOB");
    msg.setPipelineRunId(savedPipeline.getId());
    msg.setStageName("build");
    msg.setJobName("compile");
    msg.setStatus("PENDING");
    msg.setStartTime(OffsetDateTime.now());

    listener.onStatusUpdate(msg);

    Optional<JobRunEntity> found =
        jobRunRepo.findByStageRunIdAndJobName(stage.getId(), "compile");
    assertTrue(found.isPresent());
    assertEquals(RunStatus.PENDING, found.get().getStatus());
  }

  @Test
  void handleJobUpdatesExistingJobRecord() {
    // Create stage and job
    StageRunEntity stage = new StageRunEntity();
    stage.setPipelineRun(savedPipeline);
    stage.setStageName("build");
    stage.setStageOrder(0);
    stage.setStatus(RunStatus.RUNNING);
    stage.setStartTime(OffsetDateTime.now());
    stageRunRepo.save(stage);
    stageRunRepo.flush();

    JobRunEntity job = new JobRunEntity();
    job.setStageRun(stage);
    job.setJobName("compile");
    job.setStatus(RunStatus.RUNNING);
    job.setStartTime(OffsetDateTime.now());
    jobRunRepo.save(job);
    jobRunRepo.flush();

    // Update to SUCCESS
    StatusUpdateMessage msg = new StatusUpdateMessage();
    msg.setEntityType("JOB");
    msg.setPipelineRunId(savedPipeline.getId());
    msg.setStageName("build");
    msg.setJobName("compile");
    msg.setStatus("SUCCESS");
    msg.setEndTime(OffsetDateTime.now());

    listener.onStatusUpdate(msg);

    Optional<JobRunEntity> found =
        jobRunRepo.findByStageRunIdAndJobName(stage.getId(), "compile");
    assertTrue(found.isPresent());
    assertEquals(RunStatus.SUCCESS, found.get().getStatus());
    assertNotNull(found.get().getEndTime());
  }

  @Test
  void handleJobForNonExistentStageDoesNotThrow() {
    StatusUpdateMessage msg = new StatusUpdateMessage();
    msg.setEntityType("JOB");
    msg.setPipelineRunId(savedPipeline.getId());
    msg.setStageName("nosuchstage");
    msg.setJobName("compile");
    msg.setStatus("RUNNING");

    assertDoesNotThrow(() -> listener.onStatusUpdate(msg));
  }

  // ── Unknown entity type ──────────────────────────────────────────────────────

  @Test
  void handleUnknownEntityTypeDoesNotThrow() {
    StatusUpdateMessage msg = new StatusUpdateMessage();
    msg.setEntityType("UNKNOWN");
    msg.setPipelineRunId(savedPipeline.getId());
    msg.setStatus("RUNNING");

    assertDoesNotThrow(() -> listener.onStatusUpdate(msg));
  }
}

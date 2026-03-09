package cicd.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for equals(), hashCode(), toString(), and the all-args constructor
 * of all messaging POJO classes.
 *
 * <p>These tests drive coverage of the Lombok-generated methods that were missing
 * from the serialization tests.
 */
class MessageEqualsHashCodeTest {

  // ── PipelineExecuteMessage ────────────────────────────────────────────────

  @Test
  void pipelineExecuteMessage_equalObjectsAreEqual() {
    PipelineExecuteMessage msg1 = new PipelineExecuteMessage(
        1L, "default", 1, "yaml", new byte[] {1}, "main", "abc");
    PipelineExecuteMessage msg2 = new PipelineExecuteMessage(
        1L, "default", 1, "yaml", new byte[] {1}, "main", "abc");

    assertEquals(msg1, msg2);
  }

  @Test
  void pipelineExecuteMessage_equalObjectsHaveSameHashCode() {
    PipelineExecuteMessage msg1 = new PipelineExecuteMessage(
        1L, "default", 1, "yaml", new byte[] {1}, "main", "abc");
    PipelineExecuteMessage msg2 = new PipelineExecuteMessage(
        1L, "default", 1, "yaml", new byte[] {1}, "main", "abc");

    assertEquals(msg1.hashCode(), msg2.hashCode());
  }

  @Test
  void pipelineExecuteMessage_differentIdIsNotEqual() {
    PipelineExecuteMessage msg1 = new PipelineExecuteMessage(
        1L, "default", 1, "yaml", new byte[] {1}, "main", "abc");
    PipelineExecuteMessage msg2 = new PipelineExecuteMessage(
        2L, "default", 1, "yaml", new byte[] {2}, "main", "abc");

    assertNotEquals(msg1, msg2);
  }

  @Test
  void pipelineExecuteMessage_differentPipelineNameIsNotEqual() {
    PipelineExecuteMessage msg1 = new PipelineExecuteMessage(
        1L, "pipe-a", 1, "yaml", new byte[] {1}, "main", "abc");
    PipelineExecuteMessage msg2 = new PipelineExecuteMessage(
        1L, "pipe-b", 1, "yaml", new byte[] {1}, "main", "abc");

    assertNotEquals(msg1, msg2);
    assertNotEquals(msg1.hashCode(), msg2.hashCode());
  }

  @Test
  void pipelineExecuteMessage_notEqualToNull() {
    PipelineExecuteMessage msg = new PipelineExecuteMessage(
        1L, "default", 1, "yaml", new byte[] {1}, "main", "abc");

    assertNotEquals(null, msg);
  }

  @Test
  void pipelineExecuteMessage_notEqualToDifferentType() {
    PipelineExecuteMessage msg = new PipelineExecuteMessage(
        1L, "default", 1, "yaml", new byte[] {1}, "main", "abc");

    assertNotEquals("a string", msg);
  }

  @Test
  void pipelineExecuteMessage_toStringContainsFieldValues() {
    PipelineExecuteMessage msg = new PipelineExecuteMessage(
        42L, "my-pipe", 3, "yaml-content", new byte[] {4, 2}, "feature", "deadbeef");

    String str = msg.toString();
    assertNotNull(str);
    assertTrue(str.contains("my-pipe"));
    assertTrue(str.contains("deadbeef"));
  }

  @Test
  void pipelineExecuteMessage_equalToSelf() {
    PipelineExecuteMessage msg = new PipelineExecuteMessage(
        1L, "default", 1, "yaml", new byte[] {1}, "main", "abc");

    assertEquals(msg, msg);
  }

  @Test
  void pipelineExecuteMessage_nullFieldsAreHandledByEquals() {
    PipelineExecuteMessage msg1 = new PipelineExecuteMessage();
    PipelineExecuteMessage msg2 = new PipelineExecuteMessage();

    assertEquals(msg1, msg2);
    assertEquals(msg1.hashCode(), msg2.hashCode());
  }

  // ── JobResultMessage ──────────────────────────────────────────────────────

  @Test
  void jobResultMessage_equalObjectsAreEqual() {
    JobResultMessage msg1 = buildJobResult("corr-1", "compile", "build", true, 0, "OK");
    JobResultMessage msg2 = buildJobResult("corr-1", "compile", "build", true, 0, "OK");

    assertEquals(msg1, msg2);
  }

  @Test
  void jobResultMessage_equalObjectsHaveSameHashCode() {
    JobResultMessage msg1 = buildJobResult("corr-1", "compile", "build", true, 0, "OK");
    JobResultMessage msg2 = buildJobResult("corr-1", "compile", "build", true, 0, "OK");

    assertEquals(msg1.hashCode(), msg2.hashCode());
  }

  @Test
  void jobResultMessage_differentSuccessIsNotEqual() {
    JobResultMessage msg1 = buildJobResult("corr-1", "compile", "build", true, 0, "OK");
    JobResultMessage msg2 = buildJobResult("corr-1", "compile", "build", false, 1, "FAIL");

    assertNotEquals(msg1, msg2);
  }

  @Test
  void jobResultMessage_differentCorrelationIdIsNotEqual() {
    JobResultMessage msg1 = buildJobResult("corr-1", "compile", "build", true, 0, "OK");
    JobResultMessage msg2 = buildJobResult("corr-2", "compile", "build", true, 0, "OK");

    assertNotEquals(msg1, msg2);
    assertNotEquals(msg1.hashCode(), msg2.hashCode());
  }

  @Test
  void jobResultMessage_notEqualToNull() {
    JobResultMessage msg = buildJobResult("corr-1", "compile", "build", true, 0, "OK");

    assertNotEquals(null, msg);
  }

  @Test
  void jobResultMessage_equalToSelf() {
    JobResultMessage msg = buildJobResult("corr-1", "compile", "build", true, 0, "OK");

    assertEquals(msg, msg);
  }

  @Test
  void jobResultMessage_toStringContainsJobName() {
    JobResultMessage msg = buildJobResult("corr-1", "compile", "build", true, 0, "BUILD OK");

    String str = msg.toString();
    assertNotNull(str);
    assertTrue(str.contains("compile"));
  }

  @Test
  void jobResultMessage_nullFieldsAreHandledByEquals() {
    JobResultMessage msg1 = new JobResultMessage();
    JobResultMessage msg2 = new JobResultMessage();

    assertEquals(msg1, msg2);
    assertEquals(msg1.hashCode(), msg2.hashCode());
  }

  @Test
  void jobResultMessage_oneNullFieldNotEqualToNonNull() {
    JobResultMessage msg1 = new JobResultMessage();
    msg1.setJobName(null);

    JobResultMessage msg2 = new JobResultMessage();
    msg2.setJobName("compile");

    assertNotEquals(msg1, msg2);
  }

  // ── JobExecuteMessage ─────────────────────────────────────────────────────

  @Test
  void jobExecuteMessage_equalObjectsAreEqual() {
    JobExecuteMessage msg1 = buildJobExecute("corr-1", "build", "gradle:jdk21");
    JobExecuteMessage msg2 = buildJobExecute("corr-1", "build", "gradle:jdk21");

    assertEquals(msg1, msg2);
  }

  @Test
  void jobExecuteMessage_equalObjectsHaveSameHashCode() {
    JobExecuteMessage msg1 = buildJobExecute("corr-1", "build", "gradle:jdk21");
    JobExecuteMessage msg2 = buildJobExecute("corr-1", "build", "gradle:jdk21");

    assertEquals(msg1.hashCode(), msg2.hashCode());
  }

  @Test
  void jobExecuteMessage_differentImageIsNotEqual() {
    JobExecuteMessage msg1 = buildJobExecute("corr-1", "build", "gradle:jdk21");
    JobExecuteMessage msg2 = buildJobExecute("corr-1", "build", "alpine:latest");

    assertNotEquals(msg1, msg2);
    assertNotEquals(msg1.hashCode(), msg2.hashCode());
  }

  @Test
  void jobExecuteMessage_differentScriptsIsNotEqual() {
    JobExecuteMessage msg1 = buildJobExecute("corr-1", "build", "gradle:jdk21");
    msg1.setScripts(List.of("gradle build"));

    JobExecuteMessage msg2 = buildJobExecute("corr-1", "build", "gradle:jdk21");
    msg2.setScripts(List.of("gradle test"));

    assertNotEquals(msg1, msg2);
  }

  @Test
  void jobExecuteMessage_notEqualToNull() {
    JobExecuteMessage msg = buildJobExecute("corr-1", "build", "gradle:jdk21");

    assertNotEquals(null, msg);
  }

  @Test
  void jobExecuteMessage_equalToSelf() {
    JobExecuteMessage msg = buildJobExecute("corr-1", "build", "gradle:jdk21");

    assertEquals(msg, msg);
  }

  @Test
  void jobExecuteMessage_toStringContainsStageName() {
    JobExecuteMessage msg = buildJobExecute("corr-1", "build-stage", "gradle:jdk21");

    String str = msg.toString();
    assertNotNull(str);
    assertTrue(str.contains("build-stage"));
  }

  @Test
  void jobExecuteMessage_nullFieldsAreHandledByEquals() {
    JobExecuteMessage msg1 = new JobExecuteMessage();
    JobExecuteMessage msg2 = new JobExecuteMessage();

    assertEquals(msg1, msg2);
    assertEquals(msg1.hashCode(), msg2.hashCode());
  }

  // ── StatusUpdateMessage ───────────────────────────────────────────────────

  @Test
  void statusUpdateMessage_equalObjectsAreEqual() {
    StatusUpdateMessage msg1 = buildStatusUpdate("PIPELINE", 1L, "SUCCESS");
    StatusUpdateMessage msg2 = buildStatusUpdate("PIPELINE", 1L, "SUCCESS");

    assertEquals(msg1, msg2);
  }

  @Test
  void statusUpdateMessage_equalObjectsHaveSameHashCode() {
    StatusUpdateMessage msg1 = buildStatusUpdate("STAGE", 2L, "RUNNING");
    StatusUpdateMessage msg2 = buildStatusUpdate("STAGE", 2L, "RUNNING");

    assertEquals(msg1.hashCode(), msg2.hashCode());
  }

  @Test
  void statusUpdateMessage_differentEntityTypeIsNotEqual() {
    StatusUpdateMessage msg1 = buildStatusUpdate("PIPELINE", 1L, "SUCCESS");
    StatusUpdateMessage msg2 = buildStatusUpdate("STAGE", 1L, "SUCCESS");

    assertNotEquals(msg1, msg2);
  }

  @Test
  void statusUpdateMessage_differentStatusIsNotEqual() {
    StatusUpdateMessage msg1 = buildStatusUpdate("JOB", 1L, "RUNNING");
    StatusUpdateMessage msg2 = buildStatusUpdate("JOB", 1L, "SUCCESS");

    assertNotEquals(msg1, msg2);
    assertNotEquals(msg1.hashCode(), msg2.hashCode());
  }

  @Test
  void statusUpdateMessage_notEqualToNull() {
    StatusUpdateMessage msg = buildStatusUpdate("PIPELINE", 1L, "SUCCESS");

    assertNotEquals(null, msg);
  }

  @Test
  void statusUpdateMessage_equalToSelf() {
    StatusUpdateMessage msg = buildStatusUpdate("PIPELINE", 1L, "SUCCESS");

    assertEquals(msg, msg);
  }

  @Test
  void statusUpdateMessage_toStringContainsEntityType() {
    StatusUpdateMessage msg = buildStatusUpdate("PIPELINE", 1L, "SUCCESS");

    String str = msg.toString();
    assertNotNull(str);
    assertTrue(str.contains("PIPELINE"));
    assertTrue(str.contains("SUCCESS"));
  }

  @Test
  void statusUpdateMessage_nullFieldsAreHandledByEquals() {
    StatusUpdateMessage msg1 = new StatusUpdateMessage();
    StatusUpdateMessage msg2 = new StatusUpdateMessage();

    assertEquals(msg1, msg2);
    assertEquals(msg1.hashCode(), msg2.hashCode());
  }

  @Test
  void statusUpdateMessage_withTimestampFieldsAreEqual() {
    OffsetDateTime time = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");
    StatusUpdateMessage msg1 = buildStatusUpdate("JOB", 5L, "FAILED");
    msg1.setStartTime(time);
    msg1.setEndTime(time.plusMinutes(2));

    StatusUpdateMessage msg2 = buildStatusUpdate("JOB", 5L, "FAILED");
    msg2.setStartTime(time);
    msg2.setEndTime(time.plusMinutes(2));

    assertEquals(msg1, msg2);
    assertEquals(msg1.hashCode(), msg2.hashCode());
  }

  @Test
  void statusUpdateMessage_differentStartTimeIsNotEqual() {
    OffsetDateTime time1 = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");
    OffsetDateTime time2 = OffsetDateTime.parse("2025-01-01T11:00:00+00:00");

    StatusUpdateMessage msg1 = buildStatusUpdate("JOB", 5L, "RUNNING");
    msg1.setStartTime(time1);

    StatusUpdateMessage msg2 = buildStatusUpdate("JOB", 5L, "RUNNING");
    msg2.setStartTime(time2);

    assertNotEquals(msg1, msg2);
  }

  @Test
  void statusUpdateMessage_stageOrderFieldAffectsEquality() {
    StatusUpdateMessage msg1 = buildStatusUpdate("STAGE", 1L, "RUNNING");
    msg1.setStageName("build");
    msg1.setStageOrder(0);

    StatusUpdateMessage msg2 = buildStatusUpdate("STAGE", 1L, "RUNNING");
    msg2.setStageName("build");
    msg2.setStageOrder(1);

    assertNotEquals(msg1, msg2);
  }

  // ── StatusEventMessage ────────────────────────────────────────────────────

  @Test
  void statusEventMessage_allArgsConstructorSetsAllFields() {
    OffsetDateTime ts = OffsetDateTime.parse("2025-06-15T12:00:00+00:00");
    StatusEventMessage msg = new StatusEventMessage(
        "job.completed", 7L, "my-pipe", 3,
        "build", "compile", "SUCCESS", ts, "Job completed OK");

    assertEquals("job.completed", msg.getEventType());
    assertEquals(7L, msg.getPipelineRunId());
    assertEquals("my-pipe", msg.getPipelineName());
    assertEquals(3, msg.getRunNo());
    assertEquals("build", msg.getStageName());
    assertEquals("compile", msg.getJobName());
    assertEquals("SUCCESS", msg.getStatus());
    assertEquals(ts, msg.getTimestamp());
    assertEquals("Job completed OK", msg.getMessage());
  }

  @Test
  void statusEventMessage_equalObjectsAreEqual() {
    OffsetDateTime ts = OffsetDateTime.parse("2025-06-15T12:00:00+00:00");
    StatusEventMessage msg1 = new StatusEventMessage(
        "pipeline.started", 1L, "default", 1,
        null, null, "RUNNING", ts, "Pipeline started");
    StatusEventMessage msg2 = new StatusEventMessage(
        "pipeline.started", 1L, "default", 1,
        null, null, "RUNNING", ts, "Pipeline started");

    assertEquals(msg1, msg2);
  }

  @Test
  void statusEventMessage_equalObjectsHaveSameHashCode() {
    OffsetDateTime ts = OffsetDateTime.parse("2025-06-15T12:00:00+00:00");
    StatusEventMessage msg1 = new StatusEventMessage(
        "stage.done", 2L, "pipe", 1, "build", null, "SUCCESS", ts, null);
    StatusEventMessage msg2 = new StatusEventMessage(
        "stage.done", 2L, "pipe", 1, "build", null, "SUCCESS", ts, null);

    assertEquals(msg1.hashCode(), msg2.hashCode());
  }

  @Test
  void statusEventMessage_differentEventTypeIsNotEqual() {
    OffsetDateTime ts = OffsetDateTime.parse("2025-06-15T12:00:00+00:00");
    StatusEventMessage msg1 = new StatusEventMessage(
        "pipeline.started", 1L, "default", 1, null, null, "RUNNING", ts, null);
    StatusEventMessage msg2 = new StatusEventMessage(
        "pipeline.ended", 1L, "default", 1, null, null, "RUNNING", ts, null);

    assertNotEquals(msg1, msg2);
  }

  @Test
  void statusEventMessage_differentRunNoIsNotEqual() {
    OffsetDateTime ts = OffsetDateTime.parse("2025-06-15T12:00:00+00:00");
    StatusEventMessage msg1 = new StatusEventMessage(
        "ev", 1L, "pipe", 1, null, null, "RUNNING", ts, null);
    StatusEventMessage msg2 = new StatusEventMessage(
        "ev", 1L, "pipe", 2, null, null, "RUNNING", ts, null);

    assertNotEquals(msg1, msg2);
    assertNotEquals(msg1.hashCode(), msg2.hashCode());
  }

  @Test
  void statusEventMessage_notEqualToNull() {
    StatusEventMessage msg = new StatusEventMessage(
        "ev", 1L, "pipe", 1, null, null, "RUNNING",
        OffsetDateTime.now(), null);

    assertNotEquals(null, msg);
  }

  @Test
  void statusEventMessage_equalToSelf() {
    StatusEventMessage msg = new StatusEventMessage(
        "ev", 1L, "pipe", 1, "stage", "job", "SUCCESS",
        OffsetDateTime.now(), "msg");

    assertEquals(msg, msg);
  }

  @Test
  void statusEventMessage_toStringContainsEventType() {
    StatusEventMessage msg = new StatusEventMessage(
        "pipeline.completed", 1L, "release-pipe", 5,
        null, null, "SUCCESS", OffsetDateTime.now(), "Done");

    String str = msg.toString();
    assertNotNull(str);
    assertTrue(str.contains("pipeline.completed"));
    assertTrue(str.contains("release-pipe"));
  }

  @Test
  void statusEventMessage_noArgsConstructorThenSettersSupportEquality() {
    StatusEventMessage msg1 = new StatusEventMessage();
    msg1.setEventType("job.failed");
    msg1.setPipelineRunId(3L);
    msg1.setStatus("FAILED");

    StatusEventMessage msg2 = new StatusEventMessage();
    msg2.setEventType("job.failed");
    msg2.setPipelineRunId(3L);
    msg2.setStatus("FAILED");

    assertEquals(msg1, msg2);
    assertEquals(msg1.hashCode(), msg2.hashCode());
  }

  @Test
  void statusEventMessage_nullFieldsAreHandledByEquals() {
    StatusEventMessage msg1 = new StatusEventMessage();
    StatusEventMessage msg2 = new StatusEventMessage();

    assertEquals(msg1, msg2);
    assertEquals(msg1.hashCode(), msg2.hashCode());
  }

  // ── StatusUpdateMessage hashCode null-field branches ─────────────────────

  @Test
  void statusUpdateMessage_hashCodeWithNonNullFields() {
    StatusUpdateMessage msg = new StatusUpdateMessage();
    msg.setEntityType("PIPELINE");
    msg.setPipelineRunId(5L);
    msg.setPipelineName("pipe");
    msg.setStageName("build");
    msg.setStageOrder(0);
    msg.setJobName("compile");
    msg.setStatus("RUNNING");
    msg.setStartTime(OffsetDateTime.parse("2025-01-01T10:00:00+00:00"));
    msg.setEndTime(OffsetDateTime.parse("2025-01-01T10:05:00+00:00"));

    // just call hashCode; the branch is that all fields are non-null
    int hash = msg.hashCode();
    assertEquals(hash, msg.hashCode());
  }

  @Test
  void statusUpdateMessage_equalsWithMixedNullAndNonNullFields() {
    StatusUpdateMessage msg1 = new StatusUpdateMessage();
    msg1.setEntityType("JOB");
    msg1.setStageName("build");
    msg1.setJobName("compile");
    // leave other fields null

    StatusUpdateMessage msg2 = new StatusUpdateMessage();
    msg2.setEntityType("JOB");
    msg2.setStageName("build");
    msg2.setJobName("lint");
    // different job name

    assertNotEquals(msg1, msg2);
  }

  @Test
  void statusUpdateMessage_equalsFirstFieldNullSecondNotNull() {
    StatusUpdateMessage msg1 = new StatusUpdateMessage();
    msg1.setEntityType(null);
    msg1.setPipelineRunId(1L);

    StatusUpdateMessage msg2 = new StatusUpdateMessage();
    msg2.setEntityType("PIPELINE");
    msg2.setPipelineRunId(1L);

    assertNotEquals(msg1, msg2);
  }

  // ── StatusEventMessage hashCode null-field branches ───────────────────────

  @Test
  void statusEventMessage_hashCodeWithAllNonNullFields() {
    OffsetDateTime ts = OffsetDateTime.parse("2025-06-01T10:00:00+00:00");
    StatusEventMessage msg = new StatusEventMessage(
        "job.done", 10L, "pipe", 2, "build", "compile", "SUCCESS", ts, "Done");

    int hash = msg.hashCode();
    assertEquals(hash, msg.hashCode());
  }

  @Test
  void statusEventMessage_equalsWithFirstFieldNullVsNonNull() {
    StatusEventMessage msg1 = new StatusEventMessage();
    msg1.setEventType(null);
    msg1.setPipelineRunId(1L);

    StatusEventMessage msg2 = new StatusEventMessage();
    msg2.setEventType("pipeline.started");
    msg2.setPipelineRunId(1L);

    assertNotEquals(msg1, msg2);
  }

  @Test
  void statusEventMessage_equalsSecondFieldDiffersWhenFirstMatches() {
    OffsetDateTime ts = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");
    StatusEventMessage msg1 = new StatusEventMessage(
        "ev", null, "pipe", 1, null, null, "RUNNING", ts, null);
    StatusEventMessage msg2 = new StatusEventMessage(
        "ev", 99L, "pipe", 1, null, null, "RUNNING", ts, null);

    assertNotEquals(msg1, msg2);
  }

  // ── PipelineExecuteMessage detailed null combos ───────────────────────────

  @Test
  void pipelineExecuteMessage_equalsOneNullOneNonNullPipelineName() {
    PipelineExecuteMessage msg1 = new PipelineExecuteMessage();
    msg1.setPipelineName(null);

    PipelineExecuteMessage msg2 = new PipelineExecuteMessage();
    msg2.setPipelineName("pipe");

    assertNotEquals(msg1, msg2);
  }

  @Test
  void pipelineExecuteMessage_hashCodeWithAllNonNullFields() {
    PipelineExecuteMessage msg = new PipelineExecuteMessage(
        5L, "pipe", 2, "yaml-content", new byte[] {5}, "main", "abc123");

    int hash = msg.hashCode();
    assertEquals(hash, msg.hashCode());
  }

  // ── JobResultMessage detailed null combos ─────────────────────────────────

  @Test
  void jobResultMessage_equalsSecondFieldDiffersWhenFirstMatches() {
    // First field (pipelineRunId) matches but second (correlationId) differs
    JobResultMessage msg1 = new JobResultMessage();
    msg1.setPipelineRunId(1L);
    msg1.setCorrelationId("corr-A");

    JobResultMessage msg2 = new JobResultMessage();
    msg2.setPipelineRunId(1L);
    msg2.setCorrelationId("corr-B");

    assertNotEquals(msg1, msg2);
  }

  @Test
  void jobResultMessage_hashCodeWithAllNonNullFields() {
    JobResultMessage msg = buildJobResult("corr-1", "compile", "build", true, 0, "OK");
    msg.setPipelineRunId(7L);

    int hash = msg.hashCode();
    assertEquals(hash, msg.hashCode());
  }

  // ── JobExecuteMessage detailed null combos ────────────────────────────────

  @Test
  void jobExecuteMessage_equalsSecondFieldDiffersWhenFirstMatches() {
    JobExecuteMessage msg1 = new JobExecuteMessage();
    msg1.setPipelineRunId(1L);
    msg1.setCorrelationId("corr-A");

    JobExecuteMessage msg2 = new JobExecuteMessage();
    msg2.setPipelineRunId(1L);
    msg2.setCorrelationId("corr-B");

    assertNotEquals(msg1, msg2);
  }

  @Test
  void jobExecuteMessage_hashCodeWithAllNonNullFields() {
    JobExecuteMessage msg = buildJobExecute("corr-1", "build", "gradle:jdk21");
    msg.setScripts(Arrays.asList("gradle build", "gradle test"));

    int hash = msg.hashCode();
    assertEquals(hash, msg.hashCode());
  }

  // ── canEqual subclass override (triggers false branch of canEqual check) ──

  @Test
  void pipelineExecuteMessage_subclassCanEqualReturnsFalse() {
    PipelineExecuteMessage base = new PipelineExecuteMessage(
        1L, "pipe", 1, "yaml", new byte[] {1}, "main", "abc");
    PipelineExecuteMessage sub = new PipelineExecuteMessage(
        1L, "pipe", 1, "yaml", new byte[] {1}, "main", "abc") {
      @Override
      public boolean canEqual(Object other) {
        return false;
      }
    };

    // base.equals(sub) should be false because sub.canEqual(base) returns false
    assertNotEquals(base, sub);
  }

  @Test
  void jobResultMessage_subclassCanEqualReturnsFalse() {
    JobResultMessage base = buildJobResult("c", "compile", "build", true, 0, "OK");
    JobResultMessage sub = new JobResultMessage() {
      @Override
      public boolean canEqual(Object other) {
        return false;
      }
    };
    sub.setCorrelationId("c");
    sub.setJobName("compile");
    sub.setStageName("build");
    sub.setSuccess(true);

    assertNotEquals(base, sub);
  }

  @Test
  void jobExecuteMessage_subclassCanEqualReturnsFalse() {
    JobExecuteMessage base = buildJobExecute("c", "build", "gradle:jdk21");
    JobExecuteMessage sub = new JobExecuteMessage() {
      @Override
      public boolean canEqual(Object other) {
        return false;
      }
    };
    sub.setCorrelationId("c");
    sub.setStageName("build");
    sub.setImage("gradle:jdk21");

    assertNotEquals(base, sub);
  }

  @Test
  void statusUpdateMessage_subclassCanEqualReturnsFalse() {
    StatusUpdateMessage base = buildStatusUpdate("PIPELINE", 1L, "SUCCESS");
    StatusUpdateMessage sub = new StatusUpdateMessage() {
      @Override
      public boolean canEqual(Object other) {
        return false;
      }
    };
    sub.setEntityType("PIPELINE");
    sub.setPipelineRunId(1L);
    sub.setStatus("SUCCESS");

    assertNotEquals(base, sub);
  }

  @Test
  void statusEventMessage_subclassCanEqualReturnsFalse() {
    StatusEventMessage base = new StatusEventMessage();
    base.setEventType("ev");
    base.setStatus("RUNNING");

    StatusEventMessage sub = new StatusEventMessage() {
      @Override
      public boolean canEqual(Object other) {
        return false;
      }
    };
    sub.setEventType("ev");
    sub.setStatus("RUNNING");

    assertNotEquals(base, sub);
  }

  // ── null field in one, set in other - reverse direction ───────────────────

  @Test
  void pipelineExecuteMessage_equalsNonNullFieldVsNullOtherSide() {
    // Reverse of the one-null test: first has the value, second has null
    PipelineExecuteMessage msg1 = new PipelineExecuteMessage();
    msg1.setPipelineName("pipe");

    PipelineExecuteMessage msg2 = new PipelineExecuteMessage();
    msg2.setPipelineName(null);

    assertNotEquals(msg1, msg2);
  }

  @Test
  void jobResultMessage_equalsNonNullFieldVsNullOtherSide() {
    JobResultMessage msg1 = new JobResultMessage();
    msg1.setJobName("compile");

    JobResultMessage msg2 = new JobResultMessage();
    msg2.setJobName(null);

    assertNotEquals(msg1, msg2);
  }

  // ── Cross-type inequality ─────────────────────────────────────────────────

  @Test
  void differentMessageTypesAreNotEqual() {
    JobResultMessage result = new JobResultMessage();
    result.setPipelineName("pipe");

    JobExecuteMessage execute = new JobExecuteMessage();
    execute.setPipelineName("pipe");

    assertFalse(result.equals(execute));
    assertFalse(execute.equals(result));
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private JobResultMessage buildJobResult(
      String corrId, String jobName, String stageName,
      boolean success, int exitCode, String output) {
    JobResultMessage msg = new JobResultMessage();
    msg.setPipelineRunId(1L);
    msg.setCorrelationId(corrId);
    msg.setJobName(jobName);
    msg.setStageName(stageName);
    msg.setPipelineName("default");
    msg.setSuccess(success);
    msg.setExitCode(exitCode);
    msg.setOutput(output);
    return msg;
  }

  private JobExecuteMessage buildJobExecute(
      String corrId, String stageName, String image) {
    JobExecuteMessage msg = new JobExecuteMessage();
    msg.setPipelineRunId(1L);
    msg.setCorrelationId(corrId);
    msg.setJobName("compile");
    msg.setStageName(stageName);
    msg.setPipelineName("default");
    msg.setRunNo(1);
    msg.setImage(image);
    msg.setScripts(Arrays.asList("gradle build"));
    msg.setWorkspacePath("/repo");
    msg.setTotalJobsInWave(1);
    return msg;
  }

  private StatusUpdateMessage buildStatusUpdate(
      String entityType, long runId, String status) {
    StatusUpdateMessage msg = new StatusUpdateMessage();
    msg.setEntityType(entityType);
    msg.setPipelineRunId(runId);
    msg.setPipelineName("default");
    msg.setRunNo(1);
    msg.setStatus(status);
    return msg;
  }
}

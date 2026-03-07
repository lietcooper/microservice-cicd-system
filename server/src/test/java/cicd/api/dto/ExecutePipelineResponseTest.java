package cicd.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cicd.persistence.entity.PipelineRunEntity;
import cicd.persistence.entity.RunStatus;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/**
 * Tests for ExecutePipelineResponse factory method, getters/setters,
 * equals, hashCode, toString, and canEqual.
 */
class ExecutePipelineResponseTest {

  private PipelineRunEntity buildEntity(RunStatus status) {
    PipelineRunEntity entity = new PipelineRunEntity();
    entity.setPipelineName("test-pipeline");
    entity.setRunNo(5);
    entity.setStatus(status);
    entity.setStartTime(OffsetDateTime.now());
    entity.setGitBranch("main");
    entity.setGitHash("abc123");
    return entity;
  }

  @Test
  void fromEntityMapsAllFields() {
    PipelineRunEntity entity = buildEntity(RunStatus.SUCCESS);
    entity.setEndTime(OffsetDateTime.now());

    ExecutePipelineResponse response = ExecutePipelineResponse.fromEntity(entity);

    assertEquals("test-pipeline", response.getPipelineName());
    assertEquals(5, response.getRunNo());
    assertEquals(RunStatus.SUCCESS, response.getStatus());
    assertNotNull(response.getStartTime());
    assertNotNull(response.getEndTime());
    assertEquals("main", response.getGitBranch());
    assertEquals("abc123", response.getGitHash());
  }

  @Test
  void fromEntitySuccessMessage() {
    ExecutePipelineResponse response =
        ExecutePipelineResponse.fromEntity(buildEntity(RunStatus.SUCCESS));
    assertEquals("Pipeline executed successfully", response.getMessage());
  }

  @Test
  void fromEntityFailedMessage() {
    ExecutePipelineResponse response =
        ExecutePipelineResponse.fromEntity(buildEntity(RunStatus.FAILED));
    assertEquals("Pipeline execution failed", response.getMessage());
  }

  @Test
  void fromEntityPendingMessage() {
    ExecutePipelineResponse response =
        ExecutePipelineResponse.fromEntity(buildEntity(RunStatus.PENDING));
    assertEquals("Pipeline execution queued", response.getMessage());
  }

  @Test
  void fromEntityRunningMessage() {
    ExecutePipelineResponse response =
        ExecutePipelineResponse.fromEntity(buildEntity(RunStatus.RUNNING));
    assertEquals("Pipeline is running", response.getMessage());
  }

  @Test
  void fromEntityWithNullEndTime() {
    PipelineRunEntity entity = buildEntity(RunStatus.RUNNING);
    entity.setEndTime(null);

    ExecutePipelineResponse response = ExecutePipelineResponse.fromEntity(entity);
    assertNull(response.getEndTime());
  }

  @Test
  void fromEntityWithNullId() {
    // New entity without persisted ID has null id
    PipelineRunEntity entity = buildEntity(RunStatus.PENDING);
    ExecutePipelineResponse response = ExecutePipelineResponse.fromEntity(entity);
    assertNull(response.getRunId());
  }

  // ── getters and setters ───────────────────────────────────────────────────

  @Test
  void defaultConstructorLeavesFieldsNull() {
    ExecutePipelineResponse resp = new ExecutePipelineResponse();

    assertNull(resp.getRunId());
    assertNull(resp.getPipelineName());
    assertEquals(0, resp.getRunNo());
    assertNull(resp.getStatus());
    assertNull(resp.getStartTime());
    assertNull(resp.getEndTime());
    assertNull(resp.getGitBranch());
    assertNull(resp.getGitHash());
    assertNull(resp.getMessage());
  }

  @Test
  void settersAndGettersWorkCorrectly() {
    OffsetDateTime start = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");
    OffsetDateTime end = OffsetDateTime.parse("2025-01-01T10:05:00+00:00");

    ExecutePipelineResponse resp = new ExecutePipelineResponse();
    resp.setRunId(99L);
    resp.setPipelineName("release");
    resp.setRunNo(7);
    resp.setStatus(RunStatus.SUCCESS);
    resp.setStartTime(start);
    resp.setEndTime(end);
    resp.setGitBranch("main");
    resp.setGitHash("feedface");
    resp.setMessage("All good");

    assertEquals(99L, resp.getRunId());
    assertEquals("release", resp.getPipelineName());
    assertEquals(7, resp.getRunNo());
    assertEquals(RunStatus.SUCCESS, resp.getStatus());
    assertEquals(start, resp.getStartTime());
    assertEquals(end, resp.getEndTime());
    assertEquals("main", resp.getGitBranch());
    assertEquals("feedface", resp.getGitHash());
    assertEquals("All good", resp.getMessage());
  }

  // ── equals ────────────────────────────────────────────────────────────────

  @Test
  void equalsReturnsTrueForIdenticalObjects() {
    ExecutePipelineResponse r1 = buildFullResponse("pipe", RunStatus.SUCCESS);
    ExecutePipelineResponse r2 = buildFullResponse("pipe", RunStatus.SUCCESS);

    assertEquals(r1, r2);
  }

  @Test
  void equalsReturnsFalseForDifferentPipelineName() {
    ExecutePipelineResponse r1 = buildFullResponse("pipe-a", RunStatus.SUCCESS);
    ExecutePipelineResponse r2 = buildFullResponse("pipe-b", RunStatus.SUCCESS);

    assertNotEquals(r1, r2);
  }

  @Test
  void equalsReturnsFalseForDifferentStatus() {
    ExecutePipelineResponse r1 = buildFullResponse("pipe", RunStatus.SUCCESS);
    ExecutePipelineResponse r2 = buildFullResponse("pipe", RunStatus.FAILED);

    assertNotEquals(r1, r2);
  }

  @Test
  void equalsReturnsFalseForNull() {
    ExecutePipelineResponse resp = buildFullResponse("pipe", RunStatus.RUNNING);

    assertNotEquals(null, resp);
  }

  @Test
  void equalsReturnsTrueForSameInstance() {
    ExecutePipelineResponse resp = buildFullResponse("pipe", RunStatus.SUCCESS);

    assertEquals(resp, resp);
  }

  @Test
  void equalsReturnsFalseForDifferentType() {
    ExecutePipelineResponse resp = buildFullResponse("pipe", RunStatus.SUCCESS);

    assertNotEquals("a string", resp);
  }

  @Test
  void equalsWithNullFieldsOnBothSides() {
    ExecutePipelineResponse r1 = new ExecutePipelineResponse();
    ExecutePipelineResponse r2 = new ExecutePipelineResponse();

    assertEquals(r1, r2);
  }

  @Test
  void equalsOneNullFieldVsNonNull() {
    ExecutePipelineResponse r1 = new ExecutePipelineResponse();
    r1.setPipelineName(null);

    ExecutePipelineResponse r2 = new ExecutePipelineResponse();
    r2.setPipelineName("not-null");

    assertNotEquals(r1, r2);
  }

  // ── hashCode ──────────────────────────────────────────────────────────────

  @Test
  void hashCodeIsConsistentForEqualObjects() {
    ExecutePipelineResponse r1 = buildFullResponse("pipe", RunStatus.SUCCESS);
    ExecutePipelineResponse r2 = buildFullResponse("pipe", RunStatus.SUCCESS);

    assertEquals(r1.hashCode(), r2.hashCode());
  }

  @Test
  void hashCodeDiffersForDifferentPipelineNames() {
    ExecutePipelineResponse r1 = buildFullResponse("pipe-a", RunStatus.SUCCESS);
    ExecutePipelineResponse r2 = buildFullResponse("pipe-b", RunStatus.SUCCESS);

    assertNotEquals(r1.hashCode(), r2.hashCode());
  }

  @Test
  void hashCodeForEmptyObject() {
    ExecutePipelineResponse resp = new ExecutePipelineResponse();
    // Just verify it does not throw
    int hash = resp.hashCode();
    assertEquals(hash, resp.hashCode());
  }

  // ── toString ──────────────────────────────────────────────────────────────

  @Test
  void toStringContainsPipelineName() {
    ExecutePipelineResponse resp = buildFullResponse("my-pipeline", RunStatus.SUCCESS);

    String str = resp.toString();
    assertNotNull(str);
    assertTrue(str.contains("my-pipeline"));
  }

  @Test
  void toStringContainsStatus() {
    ExecutePipelineResponse resp = buildFullResponse("pipe", RunStatus.FAILED);

    String str = resp.toString();
    assertTrue(str.contains("FAILED"));
  }

  // ── Helper ────────────────────────────────────────────────────────────────

  private ExecutePipelineResponse buildFullResponse(String name, RunStatus status) {
    ExecutePipelineResponse resp = new ExecutePipelineResponse();
    resp.setRunId(1L);
    resp.setPipelineName(name);
    resp.setRunNo(1);
    resp.setStatus(status);
    resp.setStartTime(OffsetDateTime.parse("2025-01-01T10:00:00+00:00"));
    resp.setGitBranch("main");
    resp.setGitHash("abc123");
    resp.setMessage("msg");
    return resp;
  }
}

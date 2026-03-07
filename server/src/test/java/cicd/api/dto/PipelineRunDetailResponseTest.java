package cicd.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for PipelineRunDetailResponse getter/setter behavior and JSON serialization.
 *
 * <p>Verifies {@code @JsonInclude(NON_NULL)} suppresses null fields and
 * {@code @JsonProperty} names match the spec.
 */
class PipelineRunDetailResponseTest {

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
  }

  @Test
  void defaultConstructorLeavesAllFieldsDefault() {
    PipelineRunDetailResponse resp = new PipelineRunDetailResponse();

    assertNull(resp.getPipelineName());
    assertEquals(0, resp.getRunNo());
    assertNull(resp.getStatus());
    assertNull(resp.getGitRepo());
    assertNull(resp.getGitBranch());
    assertNull(resp.getGitHash());
    assertNull(resp.getStartTime());
    assertNull(resp.getEndTime());
    assertNull(resp.getStages());
  }

  @Test
  void settersAndGettersWorkCorrectly() {
    OffsetDateTime start = OffsetDateTime.now();
    OffsetDateTime end = start.plusMinutes(5);

    PipelineRunDetailResponse resp = new PipelineRunDetailResponse();
    resp.setPipelineName("default");
    resp.setRunNo(3);
    resp.setStatus("SUCCESS");
    resp.setGitRepo("git@example.com:org/repo.git");
    resp.setGitBranch("main");
    resp.setGitHash("abc123");
    resp.setStartTime(start);
    resp.setEndTime(end);

    assertEquals("default", resp.getPipelineName());
    assertEquals(3, resp.getRunNo());
    assertEquals("SUCCESS", resp.getStatus());
    assertEquals("git@example.com:org/repo.git", resp.getGitRepo());
    assertEquals("main", resp.getGitBranch());
    assertEquals("abc123", resp.getGitHash());
    assertEquals(start, resp.getStartTime());
    assertEquals(end, resp.getEndTime());
  }

  @Test
  void jsonPropertyAnnotationsProduceSpecCompliantFieldNames() throws Exception {
    PipelineRunDetailResponse resp = new PipelineRunDetailResponse();
    resp.setPipelineName("test");
    resp.setRunNo(1);
    resp.setStatus("SUCCESS");
    resp.setGitRepo("/workspace");
    resp.setGitBranch("main");
    resp.setGitHash("def456");
    resp.setStartTime(OffsetDateTime.now());
    resp.setEndTime(OffsetDateTime.now());

    String json = mapper.writeValueAsString(resp);

    assertTrue(json.contains("\"run-no\":"), "Expected 'run-no'");
    assertTrue(json.contains("\"git-repo\":"), "Expected 'git-repo'");
    assertTrue(json.contains("\"git-branch\":"), "Expected 'git-branch'");
    assertTrue(json.contains("\"git-hash\":"), "Expected 'git-hash'");
    assertTrue(json.contains("\"start\":"), "Expected 'start'");
    assertTrue(json.contains("\"end\":"), "Expected 'end'");
    assertFalse(json.contains("\"runNo\":"), "Should not contain 'runNo'");
    assertFalse(json.contains("\"gitRepo\":"), "Should not contain 'gitRepo'");
  }

  @Test
  void nullEndTimeIsExcludedFromJsonWhenNonNullInclude() throws Exception {
    PipelineRunDetailResponse resp = new PipelineRunDetailResponse();
    resp.setPipelineName("pipe");
    resp.setRunNo(1);
    resp.setStatus("RUNNING");
    resp.setStartTime(OffsetDateTime.now());
    resp.setEndTime(null); // still running

    String json = mapper.writeValueAsString(resp);

    // end should be absent because it is null and @JsonInclude(NON_NULL)
    assertFalse(json.contains("\"end\":"), "Null end should be omitted");
  }

  @Test
  void nullStagesAreExcludedFromJson() throws Exception {
    PipelineRunDetailResponse resp = new PipelineRunDetailResponse();
    resp.setPipelineName("pipe");
    resp.setRunNo(1);
    resp.setStatus("SUCCESS");
    resp.setStartTime(OffsetDateTime.now());

    String json = mapper.writeValueAsString(resp);

    assertFalse(json.contains("\"stages\":"), "Null stages should be omitted");
  }

  @Test
  void stagesListIsIncludedWhenSet() throws Exception {
    StageDto stage = new StageDto();
    stage.setName("build");
    stage.setStatus("SUCCESS");
    stage.setStartTime(OffsetDateTime.now());
    stage.setEndTime(OffsetDateTime.now());

    PipelineRunDetailResponse resp = new PipelineRunDetailResponse();
    resp.setPipelineName("pipe");
    resp.setRunNo(1);
    resp.setStatus("SUCCESS");
    resp.setStartTime(OffsetDateTime.now());
    resp.setStages(List.of(stage));

    String json = mapper.writeValueAsString(resp);

    assertTrue(json.contains("\"stages\":"), "Stages should be present");
    assertTrue(json.contains("\"build\""), "Stage name should be present");
  }

  @Test
  void equalsReturnsTrueForIdenticalObjects() {
    PipelineRunDetailResponse r1 = new PipelineRunDetailResponse();
    r1.setPipelineName("pipe");
    r1.setRunNo(1);
    r1.setStatus("SUCCESS");

    PipelineRunDetailResponse r2 = new PipelineRunDetailResponse();
    r2.setPipelineName("pipe");
    r2.setRunNo(1);
    r2.setStatus("SUCCESS");

    assertEquals(r1, r2);
  }

  @Test
  void runNoDefaultIsZero() {
    PipelineRunDetailResponse resp = new PipelineRunDetailResponse();
    // runNo is a primitive int, default is 0
    assertEquals(0, resp.getRunNo());
  }

  @Test
  void stagesCanContainStageDtosWithJobs() {
    JobDto job = new JobDto();
    job.setName("compile");
    job.setStatus("SUCCESS");

    StageDto stage = new StageDto();
    stage.setName("build");
    stage.setStatus("SUCCESS");
    stage.setJobs(List.of(job));

    PipelineRunDetailResponse resp = new PipelineRunDetailResponse();
    resp.setStages(List.of(stage));

    assertNotNull(resp.getStages());
    assertEquals(1, resp.getStages().size());
    assertNotNull(resp.getStages().get(0).getJobs());
    assertEquals("compile", resp.getStages().get(0).getJobs().get(0).getName());
  }

  // ── hashCode ──────────────────────────────────────────────────────────────

  @Test
  void hashCodeIsConsistentForEqualObjects() {
    OffsetDateTime start = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");
    OffsetDateTime end = OffsetDateTime.parse("2025-01-01T10:05:00+00:00");

    PipelineRunDetailResponse r1 = buildFull("pipe", 1, "SUCCESS", start, end);
    PipelineRunDetailResponse r2 = buildFull("pipe", 1, "SUCCESS", start, end);

    assertEquals(r1.hashCode(), r2.hashCode());
  }

  @Test
  void hashCodeDiffersWhenPipelineNameDiffers() {
    PipelineRunDetailResponse r1 = new PipelineRunDetailResponse();
    r1.setPipelineName("pipe-a");

    PipelineRunDetailResponse r2 = new PipelineRunDetailResponse();
    r2.setPipelineName("pipe-b");

    assertNotEquals(r1.hashCode(), r2.hashCode());
  }

  @Test
  void hashCodeForEmptyObject() {
    PipelineRunDetailResponse resp = new PipelineRunDetailResponse();
    int hash = resp.hashCode();
    assertEquals(hash, resp.hashCode());
  }

  // ── toString ──────────────────────────────────────────────────────────────

  @Test
  void toStringContainsPipelineNameAndStatus() {
    PipelineRunDetailResponse resp = new PipelineRunDetailResponse();
    resp.setPipelineName("release-pipeline");
    resp.setStatus("SUCCESS");

    String str = resp.toString();
    assertNotNull(str);
    assertTrue(str.contains("release-pipeline"));
    assertTrue(str.contains("SUCCESS"));
  }

  @Test
  void toStringContainsRunNo() {
    PipelineRunDetailResponse resp = new PipelineRunDetailResponse();
    resp.setPipelineName("pipe");
    resp.setRunNo(42);

    String str = resp.toString();
    assertNotNull(str);
    assertTrue(str.contains("42"));
  }

  @Test
  void toStringForEmptyObject() {
    PipelineRunDetailResponse resp = new PipelineRunDetailResponse();
    String str = resp.toString();
    assertNotNull(str);
  }

  // ── equals extra branches ─────────────────────────────────────────────────

  @Test
  void equalsReturnsFalseForDifferentRunNo() {
    PipelineRunDetailResponse r1 = new PipelineRunDetailResponse();
    r1.setPipelineName("pipe");
    r1.setRunNo(1);

    PipelineRunDetailResponse r2 = new PipelineRunDetailResponse();
    r2.setPipelineName("pipe");
    r2.setRunNo(2);

    assertNotEquals(r1, r2);
  }

  @Test
  void equalsReturnsFalseForNull() {
    PipelineRunDetailResponse resp = new PipelineRunDetailResponse();
    resp.setPipelineName("pipe");

    assertNotEquals(null, resp);
  }

  @Test
  void equalsReturnsFalseForDifferentType() {
    PipelineRunDetailResponse resp = new PipelineRunDetailResponse();
    resp.setPipelineName("pipe");

    assertNotEquals("a string", resp);
  }

  @Test
  void equalsReturnsTrueForSameInstance() {
    PipelineRunDetailResponse resp = new PipelineRunDetailResponse();
    resp.setPipelineName("pipe");

    assertEquals(resp, resp);
  }

  @Test
  void equalsOneNullGitRepoVsNonNull() {
    PipelineRunDetailResponse r1 = new PipelineRunDetailResponse();
    r1.setPipelineName("pipe");
    r1.setGitRepo(null);

    PipelineRunDetailResponse r2 = new PipelineRunDetailResponse();
    r2.setPipelineName("pipe");
    r2.setGitRepo("https://github.com/org/repo");

    assertNotEquals(r1, r2);
  }

  @Test
  void equalsWithGitFields() {
    PipelineRunDetailResponse r1 = new PipelineRunDetailResponse();
    r1.setPipelineName("pipe");
    r1.setGitRepo("https://github.com/org/repo");
    r1.setGitBranch("main");
    r1.setGitHash("abc123");

    PipelineRunDetailResponse r2 = new PipelineRunDetailResponse();
    r2.setPipelineName("pipe");
    r2.setGitRepo("https://github.com/org/repo");
    r2.setGitBranch("main");
    r2.setGitHash("abc123");

    assertEquals(r1, r2);
    assertEquals(r1.hashCode(), r2.hashCode());
  }

  @Test
  void equalsReturnsFalseWhenGitHashDiffers() {
    PipelineRunDetailResponse r1 = new PipelineRunDetailResponse();
    r1.setGitHash("abc123");

    PipelineRunDetailResponse r2 = new PipelineRunDetailResponse();
    r2.setGitHash("def456");

    assertNotEquals(r1, r2);
  }

  // ── Helper ────────────────────────────────────────────────────────────────

  private PipelineRunDetailResponse buildFull(
      String pipelineName, int runNo, String status,
      OffsetDateTime start, OffsetDateTime end) {
    PipelineRunDetailResponse resp = new PipelineRunDetailResponse();
    resp.setPipelineName(pipelineName);
    resp.setRunNo(runNo);
    resp.setStatus(status);
    resp.setGitRepo("https://github.com/org/repo");
    resp.setGitBranch("main");
    resp.setGitHash("abc123");
    resp.setStartTime(start);
    resp.setEndTime(end);
    return resp;
  }
}

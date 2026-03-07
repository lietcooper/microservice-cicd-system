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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for RunSummaryDto getter/setter and JSON serialization. */
class RunSummaryDtoTest {

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
  }

  @Test
  void defaultConstructorLeavesAllFieldsDefault() {
    RunSummaryDto dto = new RunSummaryDto();

    assertEquals(0, dto.getRunNo());
    assertNull(dto.getStatus());
    assertNull(dto.getGitRepo());
    assertNull(dto.getGitBranch());
    assertNull(dto.getGitHash());
    assertNull(dto.getStartTime());
    assertNull(dto.getEndTime());
  }

  @Test
  void settersAndGettersWorkCorrectly() {
    OffsetDateTime start = OffsetDateTime.now();
    OffsetDateTime end = start.plusMinutes(3);

    RunSummaryDto dto = new RunSummaryDto();
    dto.setRunNo(7);
    dto.setStatus("FAILED");
    dto.setGitRepo("https://github.com/org/repo");
    dto.setGitBranch("dev");
    dto.setGitHash("feedface");
    dto.setStartTime(start);
    dto.setEndTime(end);

    assertEquals(7, dto.getRunNo());
    assertEquals("FAILED", dto.getStatus());
    assertEquals("https://github.com/org/repo", dto.getGitRepo());
    assertEquals("dev", dto.getGitBranch());
    assertEquals("feedface", dto.getGitHash());
    assertEquals(start, dto.getStartTime());
    assertEquals(end, dto.getEndTime());
  }

  @Test
  void jsonPropertyAnnotationsProduceSpecCompliantFieldNames() throws Exception {
    RunSummaryDto dto = new RunSummaryDto();
    dto.setRunNo(2);
    dto.setStatus("SUCCESS");
    dto.setGitRepo("/workspace");
    dto.setGitBranch("main");
    dto.setGitHash("abc123");
    dto.setStartTime(OffsetDateTime.now());
    dto.setEndTime(OffsetDateTime.now());

    String json = mapper.writeValueAsString(dto);

    assertTrue(json.contains("\"run-no\":"), "Expected 'run-no'");
    assertTrue(json.contains("\"git-repo\":"), "Expected 'git-repo'");
    assertTrue(json.contains("\"git-branch\":"), "Expected 'git-branch'");
    assertTrue(json.contains("\"git-hash\":"), "Expected 'git-hash'");
    assertTrue(json.contains("\"start\":"), "Expected 'start'");
    assertTrue(json.contains("\"end\":"), "Expected 'end'");
  }

  @Test
  void javaPropertyNamesAreNotInJson() throws Exception {
    RunSummaryDto dto = new RunSummaryDto();
    dto.setRunNo(1);
    dto.setGitRepo("/repo");
    dto.setGitBranch("main");
    dto.setGitHash("abc");
    dto.setStartTime(OffsetDateTime.now());
    dto.setEndTime(OffsetDateTime.now());

    String json = mapper.writeValueAsString(dto);

    assertFalse(json.contains("\"runNo\":"), "Should not use Java name 'runNo'");
    assertFalse(json.contains("\"gitRepo\":"), "Should not use Java name 'gitRepo'");
    assertFalse(json.contains("\"gitBranch\":"), "Should not use Java name 'gitBranch'");
    assertFalse(json.contains("\"gitHash\":"), "Should not use Java name 'gitHash'");
    assertFalse(json.contains("\"startTime\":"), "Should not use Java name 'startTime'");
    assertFalse(json.contains("\"endTime\":"), "Should not use Java name 'endTime'");
  }

  @Test
  void equalsReturnsTrueForIdenticalObjects() {
    RunSummaryDto d1 = new RunSummaryDto();
    d1.setRunNo(1);
    d1.setStatus("SUCCESS");

    RunSummaryDto d2 = new RunSummaryDto();
    d2.setRunNo(1);
    d2.setStatus("SUCCESS");

    assertEquals(d1, d2);
  }

  @Test
  void runNoCanBeSetToZero() {
    RunSummaryDto dto = new RunSummaryDto();
    dto.setRunNo(0);
    assertEquals(0, dto.getRunNo());
  }

  // ── hashCode ──────────────────────────────────────────────────────────────

  @Test
  void hashCodeIsConsistentForEqualObjects() {
    OffsetDateTime start = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");
    OffsetDateTime end = OffsetDateTime.parse("2025-01-01T10:05:00+00:00");

    RunSummaryDto dto1 = buildFull(3, "SUCCESS", "https://github.com/org/repo",
        "main", "abc123", start, end);
    RunSummaryDto dto2 = buildFull(3, "SUCCESS", "https://github.com/org/repo",
        "main", "abc123", start, end);

    assertEquals(dto1.hashCode(), dto2.hashCode());
  }

  @Test
  void hashCodeDiffersWhenRunNoDiffers() {
    RunSummaryDto dto1 = new RunSummaryDto();
    dto1.setRunNo(1);

    RunSummaryDto dto2 = new RunSummaryDto();
    dto2.setRunNo(2);

    assertNotEquals(dto1.hashCode(), dto2.hashCode());
  }

  @Test
  void hashCodeForEmptyObject() {
    RunSummaryDto dto = new RunSummaryDto();
    int hash = dto.hashCode();
    assertEquals(hash, dto.hashCode());
  }

  // ── toString ──────────────────────────────────────────────────────────────

  @Test
  void toStringContainsRunNoAndStatus() {
    RunSummaryDto dto = new RunSummaryDto();
    dto.setRunNo(5);
    dto.setStatus("FAILED");

    String str = dto.toString();
    assertNotNull(str);
    assertTrue(str.contains("5"));
    assertTrue(str.contains("FAILED"));
  }

  @Test
  void toStringContainsGitFields() {
    RunSummaryDto dto = new RunSummaryDto();
    dto.setGitRepo("https://github.com/org/repo");
    dto.setGitBranch("feature-x");
    dto.setGitHash("deadbeef");

    String str = dto.toString();
    assertNotNull(str);
    assertTrue(str.contains("deadbeef"));
  }

  @Test
  void toStringForEmptyObject() {
    RunSummaryDto dto = new RunSummaryDto();
    String str = dto.toString();
    assertNotNull(str);
  }

  // ── equals extra branches ─────────────────────────────────────────────────

  @Test
  void equalsReturnsFalseForDifferentRunNo() {
    RunSummaryDto dto1 = new RunSummaryDto();
    dto1.setRunNo(1);
    dto1.setStatus("SUCCESS");

    RunSummaryDto dto2 = new RunSummaryDto();
    dto2.setRunNo(2);
    dto2.setStatus("SUCCESS");

    assertNotEquals(dto1, dto2);
  }

  @Test
  void equalsReturnsFalseForNull() {
    RunSummaryDto dto = new RunSummaryDto();
    dto.setRunNo(1);

    assertNotEquals(null, dto);
  }

  @Test
  void equalsReturnsFalseForDifferentType() {
    RunSummaryDto dto = new RunSummaryDto();
    dto.setRunNo(1);

    assertNotEquals("a string", dto);
  }

  @Test
  void equalsReturnsTrueForSameInstance() {
    RunSummaryDto dto = new RunSummaryDto();
    dto.setRunNo(1);
    dto.setStatus("SUCCESS");

    assertEquals(dto, dto);
  }

  @Test
  void equalsOneNullGitRepoVsNonNull() {
    RunSummaryDto dto1 = new RunSummaryDto();
    dto1.setGitRepo(null);

    RunSummaryDto dto2 = new RunSummaryDto();
    dto2.setGitRepo("https://github.com/org/repo");

    assertNotEquals(dto1, dto2);
  }

  @Test
  void equalsWithTimestampFields() {
    OffsetDateTime ts = OffsetDateTime.parse("2025-06-01T08:00:00+00:00");

    RunSummaryDto dto1 = new RunSummaryDto();
    dto1.setRunNo(1);
    dto1.setStartTime(ts);
    dto1.setEndTime(ts.plusMinutes(5));

    RunSummaryDto dto2 = new RunSummaryDto();
    dto2.setRunNo(1);
    dto2.setStartTime(ts);
    dto2.setEndTime(ts.plusMinutes(5));

    assertEquals(dto1, dto2);
    assertEquals(dto1.hashCode(), dto2.hashCode());
  }

  // ── Helper ────────────────────────────────────────────────────────────────

  private RunSummaryDto buildFull(
      int runNo, String status, String gitRepo, String gitBranch,
      String gitHash, OffsetDateTime start, OffsetDateTime end) {
    RunSummaryDto dto = new RunSummaryDto();
    dto.setRunNo(runNo);
    dto.setStatus(status);
    dto.setGitRepo(gitRepo);
    dto.setGitBranch(gitBranch);
    dto.setGitHash(gitHash);
    dto.setStartTime(start);
    dto.setEndTime(end);
    return dto;
  }
}

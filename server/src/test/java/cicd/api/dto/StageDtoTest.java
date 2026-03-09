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
 * Tests for StageDto getter/setter behavior and JSON serialization.
 * Verifies @JsonInclude(NON_NULL) excludes null jobs from JSON
 * and @JsonProperty names match the spec.
 */
class StageDtoTest {

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
  }

  @Test
  void defaultConstructorLeavesFieldsNull() {
    StageDto dto = new StageDto();

    assertNull(dto.getName());
    assertNull(dto.getStatus());
    assertNull(dto.getStartTime());
    assertNull(dto.getEndTime());
    assertNull(dto.getJobs());
  }

  @Test
  void settersAndGettersWorkCorrectly() {
    OffsetDateTime start = OffsetDateTime.now();
    OffsetDateTime end = start.plusMinutes(2);

    StageDto dto = new StageDto();
    dto.setName("build");
    dto.setStatus("SUCCESS");
    dto.setStartTime(start);
    dto.setEndTime(end);

    assertEquals("build", dto.getName());
    assertEquals("SUCCESS", dto.getStatus());
    assertEquals(start, dto.getStartTime());
    assertEquals(end, dto.getEndTime());
  }

  @Test
  void jsonPropertyAnnotationsProduceSpecCompliantFieldNames() throws Exception {
    StageDto dto = new StageDto();
    dto.setName("test");
    dto.setStatus("FAILED");
    dto.setStartTime(OffsetDateTime.now());
    dto.setEndTime(OffsetDateTime.now());

    String json = mapper.writeValueAsString(dto);

    assertTrue(json.contains("\"start\":"), "Expected 'start'");
    assertTrue(json.contains("\"end\":"), "Expected 'end'");
    assertFalse(json.contains("\"startTime\":"), "Should not use 'startTime'");
    assertFalse(json.contains("\"endTime\":"), "Should not use 'endTime'");
  }

  @Test
  void nullJobsExcludedFromJsonDueToNonNullInclude() throws Exception {
    StageDto dto = new StageDto();
    dto.setName("build");
    dto.setStatus("SUCCESS");
    dto.setStartTime(OffsetDateTime.now());
    dto.setEndTime(OffsetDateTime.now());
    // jobs left null

    String json = mapper.writeValueAsString(dto);

    assertFalse(json.contains("\"jobs\":"), "Null jobs should be omitted");
  }

  @Test
  void jobsAreIncludedInJsonWhenSet() throws Exception {
    JobDto job = new JobDto();
    job.setName("compile");
    job.setStatus("SUCCESS");
    job.setStartTime(OffsetDateTime.now());
    job.setEndTime(OffsetDateTime.now());

    StageDto dto = new StageDto();
    dto.setName("build");
    dto.setStatus("SUCCESS");
    dto.setStartTime(OffsetDateTime.now());
    dto.setEndTime(OffsetDateTime.now());
    dto.setJobs(List.of(job));

    String json = mapper.writeValueAsString(dto);

    assertTrue(json.contains("\"jobs\":"), "Jobs should be present");
    assertTrue(json.contains("\"compile\""), "Job name should be present");
  }

  @Test
  void jobsListCanContainMultipleJobs() {
    JobDto job1 = new JobDto();
    job1.setName("compile");

    JobDto job2 = new JobDto();
    job2.setName("lint");

    StageDto dto = new StageDto();
    dto.setJobs(List.of(job1, job2));

    assertNotNull(dto.getJobs());
    assertEquals(2, dto.getJobs().size());
    assertEquals("compile", dto.getJobs().get(0).getName());
    assertEquals("lint", dto.getJobs().get(1).getName());
  }

  @Test
  void equalsReturnsTrueForIdenticalObjects() {
    StageDto d1 = new StageDto();
    d1.setName("build");
    d1.setStatus("SUCCESS");

    StageDto d2 = new StageDto();
    d2.setName("build");
    d2.setStatus("SUCCESS");

    assertEquals(d1, d2);
  }

  // ── hashCode ──────────────────────────────────────────────────────────────

  @Test
  void hashCodeIsConsistentForEqualObjects() {
    OffsetDateTime ts = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");

    StageDto dto1 = new StageDto();
    dto1.setName("build");
    dto1.setStatus("SUCCESS");
    dto1.setStartTime(ts);
    dto1.setEndTime(ts.plusMinutes(2));

    StageDto dto2 = new StageDto();
    dto2.setName("build");
    dto2.setStatus("SUCCESS");
    dto2.setStartTime(ts);
    dto2.setEndTime(ts.plusMinutes(2));

    assertEquals(dto1.hashCode(), dto2.hashCode());
  }

  @Test
  void hashCodeDiffersWhenNameDiffers() {
    StageDto dto1 = new StageDto();
    dto1.setName("build");

    StageDto dto2 = new StageDto();
    dto2.setName("test");

    assertNotEquals(dto1.hashCode(), dto2.hashCode());
  }

  @Test
  void hashCodeForEmptyObject() {
    StageDto dto = new StageDto();
    int hash = dto.hashCode();
    assertEquals(hash, dto.hashCode());
  }

  // ── toString ──────────────────────────────────────────────────────────────

  @Test
  void toStringContainsNameAndStatus() {
    StageDto dto = new StageDto();
    dto.setName("deploy");
    dto.setStatus("FAILED");

    String str = dto.toString();
    assertNotNull(str);
    assertTrue(str.contains("deploy"));
    assertTrue(str.contains("FAILED"));
  }

  @Test
  void toStringForEmptyObject() {
    StageDto dto = new StageDto();
    String str = dto.toString();
    assertNotNull(str);
  }

  // ── equals extra branches ─────────────────────────────────────────────────

  @Test
  void equalsReturnsFalseForDifferentStatus() {
    StageDto dto1 = new StageDto();
    dto1.setName("build");
    dto1.setStatus("SUCCESS");

    StageDto dto2 = new StageDto();
    dto2.setName("build");
    dto2.setStatus("FAILED");

    assertNotEquals(dto1, dto2);
  }

  @Test
  void equalsReturnsFalseForNull() {
    StageDto dto = new StageDto();
    dto.setName("build");

    assertNotEquals(null, dto);
  }

  @Test
  void equalsReturnsFalseForDifferentType() {
    StageDto dto = new StageDto();
    dto.setName("build");

    assertNotEquals("a string", dto);
  }

  @Test
  void equalsReturnsTrueForSameInstance() {
    StageDto dto = new StageDto();
    dto.setName("build");
    dto.setStatus("SUCCESS");

    assertEquals(dto, dto);
  }

  @Test
  void equalsOneNullNameVsNonNull() {
    StageDto dto1 = new StageDto();
    dto1.setName(null);

    StageDto dto2 = new StageDto();
    dto2.setName("build");

    assertNotEquals(dto1, dto2);
  }

  @Test
  void equalsWithJobsLists() {
    JobDto job = new JobDto();
    job.setName("compile");
    job.setStatus("SUCCESS");

    StageDto dto1 = new StageDto();
    dto1.setName("build");
    dto1.setJobs(List.of(job));

    StageDto dto2 = new StageDto();
    dto2.setName("build");
    dto2.setJobs(List.of(job));

    assertEquals(dto1, dto2);
    assertEquals(dto1.hashCode(), dto2.hashCode());
  }

  @Test
  void equalsReturnsFalseWhenJobsDiffer() {
    JobDto job1 = new JobDto();
    job1.setName("compile");

    JobDto job2 = new JobDto();
    job2.setName("lint");

    StageDto dto1 = new StageDto();
    dto1.setName("build");
    dto1.setJobs(List.of(job1));

    StageDto dto2 = new StageDto();
    dto2.setName("build");
    dto2.setJobs(List.of(job2));

    assertNotEquals(dto1, dto2);
  }
}

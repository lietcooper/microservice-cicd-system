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

/**
 * Tests for JobDto getter/setter behavior and JSON serialization.
 * Verifies @JsonProperty names match the spec (start/end instead of startTime/endTime).
 */
class JobDtoTest {

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
  }

  @Test
  void defaultConstructorLeavesFieldsNull() {
    JobDto dto = new JobDto();

    assertNull(dto.getName());
    assertNull(dto.getStatus());
    assertNull(dto.getStartTime());
    assertNull(dto.getEndTime());
  }

  @Test
  void settersAndGettersWorkCorrectly() {
    OffsetDateTime start = OffsetDateTime.now();
    OffsetDateTime end = start.plusSeconds(30);

    JobDto dto = new JobDto();
    dto.setName("compile");
    dto.setStatus("SUCCESS");
    dto.setStartTime(start);
    dto.setEndTime(end);

    assertEquals("compile", dto.getName());
    assertEquals("SUCCESS", dto.getStatus());
    assertEquals(start, dto.getStartTime());
    assertEquals(end, dto.getEndTime());
  }

  @Test
  void jsonPropertyAnnotationsProduceSpecCompliantFieldNames() throws Exception {
    JobDto dto = new JobDto();
    dto.setName("unittest");
    dto.setStatus("FAILED");
    dto.setStartTime(OffsetDateTime.now());
    dto.setEndTime(OffsetDateTime.now());

    String json = mapper.writeValueAsString(dto);

    assertTrue(json.contains("\"start\":"), "Expected 'start' not 'startTime'");
    assertTrue(json.contains("\"end\":"), "Expected 'end' not 'endTime'");
    assertFalse(json.contains("\"startTime\":"), "Should not contain 'startTime'");
    assertFalse(json.contains("\"endTime\":"), "Should not contain 'endTime'");
  }

  @Test
  void nameAndStatusFieldNamesAreUnchanged() throws Exception {
    JobDto dto = new JobDto();
    dto.setName("build-job");
    dto.setStatus("RUNNING");
    dto.setStartTime(OffsetDateTime.now());

    String json = mapper.writeValueAsString(dto);

    assertTrue(json.contains("\"name\":"), "Expected 'name' field");
    assertTrue(json.contains("\"status\":"), "Expected 'status' field");
    assertTrue(json.contains("\"build-job\""), "Expected name value");
    assertTrue(json.contains("\"RUNNING\""), "Expected status value");
  }

  @Test
  void nullEndTimeIsIncludedAsNullInJson() throws Exception {
    // JobDto does NOT have @JsonInclude(NON_NULL) so null fields should appear
    JobDto dto = new JobDto();
    dto.setName("compile");
    dto.setStatus("RUNNING");
    dto.setStartTime(OffsetDateTime.now());
    // endTime left null

    String json = mapper.writeValueAsString(dto);
    // null fields are serialized as null by default
    assertTrue(json.contains("\"end\":null"), "Null endTime should serialize as null");
  }

  @Test
  void equalsReturnsTrueForIdenticalObjects() {
    JobDto d1 = new JobDto();
    d1.setName("compile");
    d1.setStatus("SUCCESS");

    JobDto d2 = new JobDto();
    d2.setName("compile");
    d2.setStatus("SUCCESS");

    assertEquals(d1, d2);
  }

  @Test
  void statusCanRepresentAllExpectedValues() {
    for (String status : new String[]{"SUCCESS", "FAILED", "RUNNING", "PENDING"}) {
      JobDto dto = new JobDto();
      dto.setStatus(status);
      assertEquals(status, dto.getStatus());
    }
  }

  // ── hashCode ──────────────────────────────────────────────────────────────

  @Test
  void hashCodeIsConsistentForEqualObjects() {
    OffsetDateTime ts = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");

    JobDto dto1 = new JobDto();
    dto1.setName("compile");
    dto1.setStatus("SUCCESS");
    dto1.setStartTime(ts);
    dto1.setEndTime(ts.plusSeconds(30));

    JobDto dto2 = new JobDto();
    dto2.setName("compile");
    dto2.setStatus("SUCCESS");
    dto2.setStartTime(ts);
    dto2.setEndTime(ts.plusSeconds(30));

    assertEquals(dto1.hashCode(), dto2.hashCode());
  }

  @Test
  void hashCodeDiffersWhenNameDiffers() {
    JobDto dto1 = new JobDto();
    dto1.setName("compile");

    JobDto dto2 = new JobDto();
    dto2.setName("test");

    assertNotEquals(dto1.hashCode(), dto2.hashCode());
  }

  @Test
  void hashCodeForEmptyObject() {
    JobDto dto = new JobDto();
    // Must not throw; value must be stable
    int hash = dto.hashCode();
    assertEquals(hash, dto.hashCode());
  }

  // ── toString ──────────────────────────────────────────────────────────────

  @Test
  void toStringContainsNameAndStatus() {
    JobDto dto = new JobDto();
    dto.setName("lint");
    dto.setStatus("FAILED");

    String str = dto.toString();
    assertNotNull(str);
    assertTrue(str.contains("lint"));
    assertTrue(str.contains("FAILED"));
  }

  @Test
  void toStringForEmptyObject() {
    JobDto dto = new JobDto();
    String str = dto.toString();
    assertNotNull(str);
  }

  // ── equals branches ───────────────────────────────────────────────────────

  @Test
  void equalsReturnsFalseForDifferentStatus() {
    JobDto dto1 = new JobDto();
    dto1.setName("compile");
    dto1.setStatus("SUCCESS");

    JobDto dto2 = new JobDto();
    dto2.setName("compile");
    dto2.setStatus("FAILED");

    assertNotEquals(dto1, dto2);
  }

  @Test
  void equalsReturnsFalseForNull() {
    JobDto dto = new JobDto();
    dto.setName("compile");
    dto.setStatus("SUCCESS");

    assertNotEquals(null, dto);
  }

  @Test
  void equalsReturnsFalseForDifferentType() {
    JobDto dto = new JobDto();
    dto.setName("compile");

    assertNotEquals("a string", dto);
  }

  @Test
  void equalsOneNullNameVsNonNull() {
    JobDto dto1 = new JobDto();
    dto1.setName(null);

    JobDto dto2 = new JobDto();
    dto2.setName("compile");

    assertNotEquals(dto1, dto2);
  }

  @Test
  void equalsWithNullTimestamps() {
    JobDto dto1 = new JobDto();
    dto1.setName("job");
    dto1.setStatus("RUNNING");

    JobDto dto2 = new JobDto();
    dto2.setName("job");
    dto2.setStatus("RUNNING");

    assertEquals(dto1, dto2);
    assertEquals(dto1.hashCode(), dto2.hashCode());
  }

  @Test
  void equalsReturnsFalseWhenStartTimeDiffers() {
    OffsetDateTime ts1 = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");
    OffsetDateTime ts2 = OffsetDateTime.parse("2025-01-01T11:00:00+00:00");

    JobDto dto1 = new JobDto();
    dto1.setName("job");
    dto1.setStartTime(ts1);

    JobDto dto2 = new JobDto();
    dto2.setName("job");
    dto2.setStartTime(ts2);

    assertNotEquals(dto1, dto2);
  }
}

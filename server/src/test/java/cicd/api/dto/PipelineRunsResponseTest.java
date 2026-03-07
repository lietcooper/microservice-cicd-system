package cicd.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for PipelineRunsResponse getter/setter and list behavior. */
class PipelineRunsResponseTest {

  @Test
  void defaultConstructorLeavesFieldsNull() {
    PipelineRunsResponse response = new PipelineRunsResponse();

    assertNull(response.getPipelineName());
    assertNull(response.getRuns());
  }

  @Test
  void setPipelineNameStoresValue() {
    PipelineRunsResponse response = new PipelineRunsResponse();
    response.setPipelineName("default");
    assertEquals("default", response.getPipelineName());
  }

  @Test
  void setRunsStoresEmptyList() {
    PipelineRunsResponse response = new PipelineRunsResponse();
    response.setRuns(new ArrayList<>());
    assertNotNull(response.getRuns());
    assertTrue(response.getRuns().isEmpty());
  }

  @Test
  void setRunsStoresListWithMultipleItems() {
    OffsetDateTime now = OffsetDateTime.now();

    RunSummaryDto run1 = new RunSummaryDto();
    run1.setRunNo(1);
    run1.setStatus("SUCCESS");
    run1.setStartTime(now);

    RunSummaryDto run2 = new RunSummaryDto();
    run2.setRunNo(2);
    run2.setStatus("FAILED");
    run2.setStartTime(now);

    PipelineRunsResponse response = new PipelineRunsResponse();
    response.setPipelineName("mypipe");
    response.setRuns(List.of(run1, run2));

    assertEquals("mypipe", response.getPipelineName());
    assertEquals(2, response.getRuns().size());
    assertEquals(1, response.getRuns().get(0).getRunNo());
    assertEquals(2, response.getRuns().get(1).getRunNo());
  }

  @Test
  void runsListPreservesOrder() {
    List<RunSummaryDto> runs = new ArrayList<>();
    for (int ii = 5; ii >= 1; ii--) {
      RunSummaryDto dto = new RunSummaryDto();
      dto.setRunNo(ii);
      runs.add(dto);
    }

    PipelineRunsResponse response = new PipelineRunsResponse();
    response.setRuns(runs);

    assertEquals(5, response.getRuns().get(0).getRunNo());
    assertEquals(1, response.getRuns().get(4).getRunNo());
  }

  @Test
  void equalsReturnsTrueForIdenticalObjects() {
    PipelineRunsResponse r1 = new PipelineRunsResponse();
    r1.setPipelineName("pipe");
    r1.setRuns(new ArrayList<>());

    PipelineRunsResponse r2 = new PipelineRunsResponse();
    r2.setPipelineName("pipe");
    r2.setRuns(new ArrayList<>());

    assertEquals(r1, r2);
  }

  // ── hashCode ──────────────────────────────────────────────────────────────

  @Test
  void hashCodeIsConsistentForEqualObjects() {
    PipelineRunsResponse r1 = new PipelineRunsResponse();
    r1.setPipelineName("my-pipe");
    r1.setRuns(new ArrayList<>());

    PipelineRunsResponse r2 = new PipelineRunsResponse();
    r2.setPipelineName("my-pipe");
    r2.setRuns(new ArrayList<>());

    assertEquals(r1.hashCode(), r2.hashCode());
  }

  @Test
  void hashCodeDiffersWhenPipelineNameDiffers() {
    PipelineRunsResponse r1 = new PipelineRunsResponse();
    r1.setPipelineName("pipe-a");

    PipelineRunsResponse r2 = new PipelineRunsResponse();
    r2.setPipelineName("pipe-b");

    assertNotEquals(r1.hashCode(), r2.hashCode());
  }

  @Test
  void hashCodeForEmptyObject() {
    PipelineRunsResponse resp = new PipelineRunsResponse();
    int hash = resp.hashCode();
    assertEquals(hash, resp.hashCode());
  }

  @Test
  void hashCodeDiffersWhenRunsListDiffers() {
    RunSummaryDto run = new RunSummaryDto();
    run.setRunNo(1);
    run.setStatus("SUCCESS");

    PipelineRunsResponse r1 = new PipelineRunsResponse();
    r1.setPipelineName("pipe");
    r1.setRuns(List.of(run));

    PipelineRunsResponse r2 = new PipelineRunsResponse();
    r2.setPipelineName("pipe");
    r2.setRuns(new ArrayList<>());

    assertNotEquals(r1.hashCode(), r2.hashCode());
  }

  // ── toString ──────────────────────────────────────────────────────────────

  @Test
  void toStringContainsPipelineName() {
    PipelineRunsResponse resp = new PipelineRunsResponse();
    resp.setPipelineName("release-pipeline");

    String str = resp.toString();
    assertNotNull(str);
    assertTrue(str.contains("release-pipeline"));
  }

  @Test
  void toStringForEmptyObject() {
    PipelineRunsResponse resp = new PipelineRunsResponse();
    String str = resp.toString();
    assertNotNull(str);
  }

  // ── equals extra branches ─────────────────────────────────────────────────

  @Test
  void equalsReturnsFalseForDifferentPipelineName() {
    PipelineRunsResponse r1 = new PipelineRunsResponse();
    r1.setPipelineName("pipe-a");
    r1.setRuns(new ArrayList<>());

    PipelineRunsResponse r2 = new PipelineRunsResponse();
    r2.setPipelineName("pipe-b");
    r2.setRuns(new ArrayList<>());

    assertNotEquals(r1, r2);
  }

  @Test
  void equalsReturnsFalseForNull() {
    PipelineRunsResponse resp = new PipelineRunsResponse();
    resp.setPipelineName("pipe");

    assertNotEquals(null, resp);
  }

  @Test
  void equalsReturnsFalseForDifferentType() {
    PipelineRunsResponse resp = new PipelineRunsResponse();
    resp.setPipelineName("pipe");

    assertNotEquals("a string", resp);
  }

  @Test
  void equalsReturnsTrueForSameInstance() {
    PipelineRunsResponse resp = new PipelineRunsResponse();
    resp.setPipelineName("pipe");

    assertEquals(resp, resp);
  }

  @Test
  void equalsOneNullPipelineNameVsNonNull() {
    PipelineRunsResponse r1 = new PipelineRunsResponse();
    r1.setPipelineName(null);

    PipelineRunsResponse r2 = new PipelineRunsResponse();
    r2.setPipelineName("pipe");

    assertNotEquals(r1, r2);
  }

  @Test
  void equalsReturnsFalseWhenRunsListDiffers() {
    RunSummaryDto run = new RunSummaryDto();
    run.setRunNo(1);

    PipelineRunsResponse r1 = new PipelineRunsResponse();
    r1.setPipelineName("pipe");
    r1.setRuns(List.of(run));

    PipelineRunsResponse r2 = new PipelineRunsResponse();
    r2.setPipelineName("pipe");
    r2.setRuns(new ArrayList<>());

    assertNotEquals(r1, r2);
  }
}

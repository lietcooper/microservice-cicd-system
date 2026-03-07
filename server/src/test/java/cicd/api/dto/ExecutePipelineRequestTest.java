package cicd.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for ExecutePipelineRequest getter/setter behavior. */
class ExecutePipelineRequestTest {

  @Test
  void defaultConstructorLeavesAllFieldsNull() {
    ExecutePipelineRequest req = new ExecutePipelineRequest();

    assertNull(req.getPipelineName());
    assertNull(req.getPipelineYaml());
    assertNull(req.getBranch());
    assertNull(req.getCommit());
    assertNull(req.getRepoPath());
  }

  @Test
  void setPipelineNameStoresValue() {
    ExecutePipelineRequest req = new ExecutePipelineRequest();
    req.setPipelineName("my-pipeline");
    assertEquals("my-pipeline", req.getPipelineName());
  }

  @Test
  void setPipelineYamlStoresValue() {
    ExecutePipelineRequest req = new ExecutePipelineRequest();
    String yaml = "pipeline:\n  name: test\n";
    req.setPipelineYaml(yaml);
    assertEquals(yaml, req.getPipelineYaml());
  }

  @Test
  void setBranchStoresValue() {
    ExecutePipelineRequest req = new ExecutePipelineRequest();
    req.setBranch("feature-x");
    assertEquals("feature-x", req.getBranch());
  }

  @Test
  void setCommitStoresValue() {
    ExecutePipelineRequest req = new ExecutePipelineRequest();
    req.setCommit("abc123def");
    assertEquals("abc123def", req.getCommit());
  }

  @Test
  void setRepoPathStoresValue() {
    ExecutePipelineRequest req = new ExecutePipelineRequest();
    req.setRepoPath("/home/user/project");
    assertEquals("/home/user/project", req.getRepoPath());
  }

  @Test
  void allFieldsCanBeSetIndependently() {
    ExecutePipelineRequest req = new ExecutePipelineRequest();
    req.setPipelineName("default");
    req.setPipelineYaml("yaml-content");
    req.setBranch("main");
    req.setCommit("deadbeef");
    req.setRepoPath("/workspace");

    assertEquals("default", req.getPipelineName());
    assertEquals("yaml-content", req.getPipelineYaml());
    assertEquals("main", req.getBranch());
    assertEquals("deadbeef", req.getCommit());
    assertEquals("/workspace", req.getRepoPath());
  }

  @Test
  void equalsReturnsTrueForIdenticalObjects() {
    ExecutePipelineRequest req1 = new ExecutePipelineRequest();
    req1.setPipelineName("pipe");
    req1.setBranch("main");

    ExecutePipelineRequest req2 = new ExecutePipelineRequest();
    req2.setPipelineName("pipe");
    req2.setBranch("main");

    assertEquals(req1, req2);
  }

  @Test
  void toStringContainsPipelineName() {
    ExecutePipelineRequest req = new ExecutePipelineRequest();
    req.setPipelineName("my-pipe");
    String str = req.toString();

    // Lombok @Data generates toString including field values
    assertTrue(str.contains("my-pipe"));
  }

  // ── hashCode ──────────────────────────────────────────────────────────────

  @Test
  void hashCodeIsConsistentForEqualObjects() {
    ExecutePipelineRequest req1 = new ExecutePipelineRequest();
    req1.setPipelineName("default");
    req1.setBranch("main");
    req1.setCommit("abc123");
    req1.setRepoPath("/workspace");

    ExecutePipelineRequest req2 = new ExecutePipelineRequest();
    req2.setPipelineName("default");
    req2.setBranch("main");
    req2.setCommit("abc123");
    req2.setRepoPath("/workspace");

    assertEquals(req1.hashCode(), req2.hashCode());
  }

  @Test
  void hashCodeDiffersWhenPipelineNameDiffers() {
    ExecutePipelineRequest req1 = new ExecutePipelineRequest();
    req1.setPipelineName("pipe-a");

    ExecutePipelineRequest req2 = new ExecutePipelineRequest();
    req2.setPipelineName("pipe-b");

    assertNotEquals(req1.hashCode(), req2.hashCode());
  }

  @Test
  void hashCodeForEmptyObject() {
    ExecutePipelineRequest req = new ExecutePipelineRequest();
    int hash = req.hashCode();
    assertEquals(hash, req.hashCode());
  }

  // ── toString additional ───────────────────────────────────────────────────

  @Test
  void toStringContainsAllSetFields() {
    ExecutePipelineRequest req = new ExecutePipelineRequest();
    req.setPipelineName("release");
    req.setBranch("main");
    req.setCommit("deadbeef");
    req.setRepoPath("/workspace");

    String str = req.toString();
    assertNotNull(str);
    assertTrue(str.contains("release"));
    assertTrue(str.contains("deadbeef"));
  }

  @Test
  void toStringForEmptyObject() {
    ExecutePipelineRequest req = new ExecutePipelineRequest();
    String str = req.toString();
    assertNotNull(str);
  }

  // ── equals extra branches ─────────────────────────────────────────────────

  @Test
  void equalsReturnsFalseForDifferentBranch() {
    ExecutePipelineRequest req1 = new ExecutePipelineRequest();
    req1.setPipelineName("pipe");
    req1.setBranch("main");

    ExecutePipelineRequest req2 = new ExecutePipelineRequest();
    req2.setPipelineName("pipe");
    req2.setBranch("feature-x");

    assertNotEquals(req1, req2);
  }

  @Test
  void equalsReturnsFalseForNull() {
    ExecutePipelineRequest req = new ExecutePipelineRequest();
    req.setPipelineName("pipe");

    assertNotEquals(null, req);
  }

  @Test
  void equalsReturnsFalseForDifferentType() {
    ExecutePipelineRequest req = new ExecutePipelineRequest();
    req.setPipelineName("pipe");

    assertNotEquals("a string", req);
  }

  @Test
  void equalsReturnsTrueForSameInstance() {
    ExecutePipelineRequest req = new ExecutePipelineRequest();
    req.setPipelineName("pipe");
    req.setBranch("main");

    assertEquals(req, req);
  }

  @Test
  void equalsOneNullFieldVsNonNull() {
    ExecutePipelineRequest req1 = new ExecutePipelineRequest();
    req1.setPipelineName(null);

    ExecutePipelineRequest req2 = new ExecutePipelineRequest();
    req2.setPipelineName("pipe");

    assertNotEquals(req1, req2);
  }

  @Test
  void equalsWithPipelineYamlField() {
    ExecutePipelineRequest req1 = new ExecutePipelineRequest();
    req1.setPipelineYaml("pipeline:\n  name: test\n");

    ExecutePipelineRequest req2 = new ExecutePipelineRequest();
    req2.setPipelineYaml("pipeline:\n  name: test\n");

    assertEquals(req1, req2);
    assertEquals(req1.hashCode(), req2.hashCode());
  }

  @Test
  void equalsReturnsFalseWhenPipelineYamlDiffers() {
    ExecutePipelineRequest req1 = new ExecutePipelineRequest();
    req1.setPipelineYaml("pipeline:\n  name: a\n");

    ExecutePipelineRequest req2 = new ExecutePipelineRequest();
    req2.setPipelineYaml("pipeline:\n  name: b\n");

    assertNotEquals(req1, req2);
  }
}

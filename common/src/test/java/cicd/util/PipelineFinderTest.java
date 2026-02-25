package cicd.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class PipelineFinderTest {

  @Test
  void testFindExisting() {
      String repoRoot = System.getProperty("user.dir"); // 项目根目录
      var result = PipelineFinder.findByName("default", repoRoot);
      assertFalse(result.hasError());
      assertEquals("default", result.pipeline.name);
  }

  @Test
  void testFindNonExisting() {
      String repoRoot = System.getProperty("user.dir");
      var result = PipelineFinder.findByName("nonexistent", repoRoot);
      assertTrue(result.hasError());
  }
}

package cicd.cmd;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PipelineFinderTest {

  @Test
  void testFindExisting() {
    var result = PipelineFinder.findByName("default");
    assertFalse(result.hasError());
    assertEquals("default", result.pipeline.name);
  }

  @Test
  void testFindNonExisting() {
    var result = PipelineFinder.findByName("nonexistent");
    assertTrue(result.hasError());
  }
}
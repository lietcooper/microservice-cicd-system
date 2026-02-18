package cicd.executor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobResultTest {

  @Test
  void testSuccessfulJob() {
    JobResult result = new JobResult("compile", 0, "Build successful");

    assertEquals("compile", result.jobName);
    assertEquals(0, result.exitCode);
    assertEquals("Build successful", result.output);
    assertTrue(result.ok());
  }

  @Test
  void testFailedJob() {
    JobResult result = new JobResult("test", 1, "Test failed: 2 errors");

    assertEquals("test", result.jobName);
    assertEquals(1, result.exitCode);
    assertEquals("Test failed: 2 errors", result.output);
    assertFalse(result.ok());
  }

  @Test
  void testNonZeroExitCode() {
    JobResult result = new JobResult("deploy", 127, "Command not found");

    assertEquals(127, result.exitCode);
    assertFalse(result.ok());
  }

  @Test
  void testNullOutput() {
    JobResult result = new JobResult("silent", 0, null);

    assertNull(result.output);
    assertTrue(result.ok());
  }

  @Test
  void testEmptyOutput() {
    JobResult result = new JobResult("quiet", 0, "");

    assertEquals("", result.output);
    assertTrue(result.ok());
  }

  @Test
  void testNegativeExitCode() {
    // Some systems use negative exit codes for signals
    JobResult result = new JobResult("killed", -9, "Killed by signal");

    assertEquals(-9, result.exitCode);
    assertFalse(result.ok());
  }
}

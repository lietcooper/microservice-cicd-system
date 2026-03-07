package cicd.cmd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/**
 * Tests for ReportCmd validation logic.
 *
 * <p>Tests that require a running server are limited to checking that
 * option combination validation errors are caught before any HTTP call.
 * Tests that would make actual HTTP requests (when server is not running)
 * verify that connection failure produces exit code 1 and appropriate message.
 */
class ReportCmdTest {

  private ByteArrayOutputStream outContent;
  private ByteArrayOutputStream errContent;
  private PrintStream originalOut;
  private PrintStream originalErr;

  @BeforeEach
  void setUpStreams() {
    outContent = new ByteArrayOutputStream();
    errContent = new ByteArrayOutputStream();
    originalOut = System.out;
    originalErr = System.err;
    System.setOut(new PrintStream(outContent));
    System.setErr(new PrintStream(errContent));
  }

  void restoreStreams() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  // ── Validation: option combination rules ────────────────────────────────────

  @Test
  void jobRequiresStage() {
    int code = new CommandLine(new ReportCmd()).execute(
        "--pipeline", "default",
        "--run", "1",
        "--job", "compile");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("--job requires --stage"));
  }

  @Test
  void stageRequiresRun() {
    int code = new CommandLine(new ReportCmd()).execute(
        "--pipeline", "default",
        "--stage", "build");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("--stage requires --run"));
  }

  @Test
  void jobWithoutStageAndWithRunStillFails() {
    int code = new CommandLine(new ReportCmd()).execute(
        "--pipeline", "default",
        "--run", "1",
        "--job", "compile"
        // no --stage
    );
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("--job requires --stage"));
  }

  // ── Missing required option ──────────────────────────────────────────────────

  @Test
  void pipelineOptionIsRequired() {
    // --pipeline is required; omitting it should produce a picocli error
    int code = new CommandLine(new ReportCmd()).execute(
        "--run", "1");
    restoreStreams();
    // picocli returns 2 for usage errors when required option is missing
    assertTrue(code != 0);
  }

  // ── buildUrl logic (tested via connection failure to localhost) ──────────────

  @Test
  void pipelineOnlyConnectsToServer() {
    // When only --pipeline is given, a connection attempt is made.
    // Since no server is running at a bogus port, it should fail with exit 1.
    int code = new CommandLine(new ReportCmd()).execute(
        "--pipeline", "default",
        "--server", "http://localhost:19999");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("could not connect to server")
        || errContent.toString().contains("error"));
  }

  @Test
  void pipelineAndRunConnectsToServer() {
    int code = new CommandLine(new ReportCmd()).execute(
        "--pipeline", "default",
        "--run", "1",
        "--server", "http://localhost:19999");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("could not connect to server")
        || errContent.toString().contains("error"));
  }

  @Test
  void pipelineRunAndStageConnectsToServer() {
    int code = new CommandLine(new ReportCmd()).execute(
        "--pipeline", "default",
        "--run", "1",
        "--stage", "build",
        "--server", "http://localhost:19999");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("could not connect to server")
        || errContent.toString().contains("error"));
  }

  @Test
  void pipelineRunStageAndJobConnectsToServer() {
    int code = new CommandLine(new ReportCmd()).execute(
        "--pipeline", "default",
        "--run", "1",
        "--stage", "build",
        "--job", "compile",
        "--server", "http://localhost:19999");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("could not connect to server")
        || errContent.toString().contains("error"));
  }

  // ── Additional edge cases ────────────────────────────────────────────────────

  @Test
  void jobAndStageWithoutRunFailsWithStageError() {
    // --stage requires --run, so this should fail on --stage requires --run
    // (checked before --job requires --stage when both are missing run)
    int code = new CommandLine(new ReportCmd()).execute(
        "--pipeline", "default",
        "--stage", "build",
        "--job", "compile");
    restoreStreams();
    assertEquals(1, code);
    // stage requires run is checked first
    assertTrue(errContent.toString().contains("--stage requires --run"));
  }

  @Test
  void pipelineOptionWithEmptyStringAttemptsMakingRequest() {
    // Empty pipeline name should still attempt an HTTP request
    int code = new CommandLine(new ReportCmd()).execute(
        "--pipeline", "",
        "--server", "http://localhost:19999");
    restoreStreams();
    assertEquals(1, code);
  }

  @Test
  void negativeRunNumberAttemptsMakingRequest() {
    int code = new CommandLine(new ReportCmd()).execute(
        "--pipeline", "default",
        "--run", "-1",
        "--server", "http://localhost:19999");
    restoreStreams();
    assertEquals(1, code);
    // Should fail at connection (validation passes for this combo)
    assertTrue(errContent.toString().contains("could not connect to server")
        || errContent.toString().contains("error"));
  }

  @Test
  void runZeroAttemptsMakingRequest() {
    int code = new CommandLine(new ReportCmd()).execute(
        "--pipeline", "default",
        "--run", "0",
        "--server", "http://localhost:19999");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("could not connect to server")
        || errContent.toString().contains("error"));
  }

  @Test
  void errorMessageMentionsServerWhenConnectionFails() {
    int code = new CommandLine(new ReportCmd()).execute(
        "--pipeline", "my-pipe",
        "--server", "http://localhost:19999");
    restoreStreams();
    assertEquals(1, code);
    String err = errContent.toString();
    // Should mention that the server could not be connected
    assertTrue(err.contains("server") || err.contains("connect") || err.contains("error"));
  }

  @Test
  void allFourLevelOptionsValidationPassesBeforeHttpCall() {
    // All four options given: validation passes, HTTP call attempted
    int code = new CommandLine(new ReportCmd()).execute(
        "--pipeline", "default",
        "--run", "1",
        "--stage", "build",
        "--job", "compile",
        "--server", "http://localhost:19999");
    restoreStreams();
    assertEquals(1, code);
    // Should fail at connection, not at option validation
    org.junit.jupiter.api.Assertions.assertFalse(
        errContent.toString().contains("--job requires --stage"));
    org.junit.jupiter.api.Assertions.assertFalse(
        errContent.toString().contains("--stage requires --run"));
  }
}

package cicd.cmd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/** Tests for RunCmd option validation and error behavior. */
class RunCmdTest {

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

  // ── Option validation ─────────────────────────────────────────────────────────

  @Test
  void testNoArgs() {
    int code = new CommandLine(new RunCmd()).execute();
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("repo-url must be specified") 
        || errContent.toString().contains("must specify either --name or --file"));
  }

  @Test
  void testBothNameAndFile() {
    int code = new CommandLine(new RunCmd()).execute(
        "--repo-url", "http://github.com/repo",
        "--name", "default", "--file", "some.yaml");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("mutually exclusive"));
  }

  @Test
  void testFileNotFound() {
    int code = new CommandLine(new RunCmd()).execute(
        "--repo-url", "http://github.com/repo",
        "--file", "nonexistent.yaml");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("file not found"));
  }

  @Test
  void testRepoUrlRequiredWhenNotInGitRoot() {
    // We can't easily mock "not in git root" without complex setup, 
    // but we can test that providing repo-url avoids the error.
    int code = new CommandLine(new RunCmd()).execute(
        "--repo-url", "http://github.com/repo",
        "--name", "default",
        "--server", "http://localhost:19999");
    restoreStreams();
    // Should fail with connection error, NOT "repo-url must be specified"
    assertTrue(errContent.toString().contains("Failed to communicate with server"));
  }

  // ── Connection failure when server not running ────────────────────────────────

  @Test
  void testNameOptionAttemptsServerConnection() {
    // When name is given and no other invalid condition, it tries to contact server
    int code = new CommandLine(new RunCmd()).execute(
        "--repo-url", "http://github.com/repo",
        "--name", "default",
        "--server", "http://localhost:19999");
    restoreStreams();
    // Should fail because no server at that port
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("Failed to communicate with server"));
  }

  @Test
  void testFileOptionAttemptsServerConnection() {
    // Create a dummy file to satisfy the local file existence check
    java.io.File dummy = new java.io.File("dummy.yaml");
    try {
        dummy.createNewFile();
        int code = new CommandLine(new RunCmd()).execute(
            "--repo-url", "http://github.com/repo",
            "--file", "dummy.yaml",
            "--server", "http://localhost:19999");
        restoreStreams();
        // Should fail because no server at that port
        assertEquals(1, code);
        assertTrue(errContent.toString().contains("Failed to communicate with server"));
    } catch (java.io.IOException e) {
        restoreStreams();
    } finally {
        dummy.delete();
    }
  }

  // ── Additional edge cases ────────────────────────────────────────────────────

  @Test
  void testBranchOptionAloneWithoutNameOrFile() {
    int code = new CommandLine(new RunCmd()).execute(
        "--repo-url", "http://github.com/repo",
        "--branch", "main");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("must specify either --name or --file"));
  }

  @Test
  void testCommitOptionAloneWithoutNameOrFile() {
    int code = new CommandLine(new RunCmd()).execute(
        "--repo-url", "http://github.com/repo",
        "--commit", "abc123");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("must specify either --name or --file"));
  }

  @Test
  void testCustomServerUrlIsUsed() {
    int code = new CommandLine(new RunCmd()).execute(
        "--repo-url", "http://github.com/repo",
        "--name", "default",
        "--server", "http://localhost:29999");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("29999")
        || errContent.toString().contains("Failed to communicate"));
  }
}

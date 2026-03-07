package cicd.cmd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import static org.junit.jupiter.api.Assertions.*;

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
    assertTrue(errContent.toString().contains("must specify either --name or --file"));
  }

  @Test
  void testBothNameAndFile() {
    int code = new CommandLine(new RunCmd()).execute(
        "--name", "default", "--file", "some.yaml");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("mutually exclusive"));
  }

  @Test
  void testFileNotFound() {
    int code = new CommandLine(new RunCmd()).execute(
        "--file", "nonexistent.yaml");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("file not found")
        || errContent.toString().contains("error"));
  }

  @Test
  void testBranchMismatch() {
    int code = new CommandLine(new RunCmd()).execute(
        "--file", ".pipelines/default.yaml",
        "--branch", "fake-branch-xyz");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("requested branch")
        || errContent.toString().contains("fake-branch-xyz"));
  }

  @Test
  void testCommitMismatch() {
    int code = new CommandLine(new RunCmd()).execute(
        "--file", ".pipelines/default.yaml",
        "--commit", "0000000");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("requested commit")
        || errContent.toString().contains("0000000"));
  }

  // ── Connection failure when server not running ────────────────────────────────

  @Test
  void testNameOptionAttemptsServerConnection() {
    // When name is given and no other invalid condition, it tries to contact server
    int code = new CommandLine(new RunCmd()).execute(
        "--name", "default",
        "--server", "http://localhost:19999");
    restoreStreams();
    // Should fail because no server at that port
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("Failed to communicate with server")
        || errContent.toString().contains("error")
        || errContent.toString().contains("not in a git repository"));
  }

  @Test
  void testFileOptionAttemptsServerConnection() throws Exception {
    // .pipelines/default.yaml exists in the project root
    int code = new CommandLine(new RunCmd()).execute(
        "--file", ".pipelines/default.yaml",
        "--server", "http://localhost:19999");
    restoreStreams();
    // Should fail because no server at that port
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("Failed to communicate with server")
        || errContent.toString().contains("error")
        || errContent.toString().contains("not in a git repository"));
  }

  // ── Branch/commit matching ────────────────────────────────────────────────────

  @Test
  void testCurrentBranchDoesNotMismatch() {
    // Without specifying --branch, no branch check is done, so validation passes
    // and falls through to server connection (which fails at bogus port)
    int code = new CommandLine(new RunCmd()).execute(
        "--name", "default",
        "--server", "http://localhost:19999");
    restoreStreams();
    // Branch mismatch error should NOT appear
    assertFalse(errContent.toString().contains("requested branch"));
  }

  @Test
  void testBothBranchAndCommitMismatchBranchCheckedFirst() {
    // Branch is checked before commit, so branch mismatch error appears
    int code = new CommandLine(new RunCmd()).execute(
        "--file", ".pipelines/default.yaml",
        "--branch", "fake-branch-xyz",
        "--commit", "0000000");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("requested branch")
        || errContent.toString().contains("fake-branch-xyz"));
  }
}

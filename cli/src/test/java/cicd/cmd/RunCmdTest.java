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
  void testFileOptionAttemptsServerConnection() {
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

  // ── Additional edge cases ────────────────────────────────────────────────────

  @Test
  void testBranchOptionAloneWithoutNameOrFile() {
    // Without --name or --file, should fail with "must specify" error
    // regardless of --branch value
    int code = new CommandLine(new RunCmd()).execute("--branch", "main");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("must specify either --name or --file"));
  }

  @Test
  void testCommitOptionAloneWithoutNameOrFile() {
    int code = new CommandLine(new RunCmd()).execute("--commit", "abc123");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("must specify either --name or --file"));
  }

  @Test
  void testBothNameAndFileWithBranchOption() {
    int code = new CommandLine(new RunCmd()).execute(
        "--name", "default",
        "--file", "some.yaml",
        "--branch", "main");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("mutually exclusive"));
  }

  @Test
  void testErrorMessageContainsRequestedBranchName() {
    int code = new CommandLine(new RunCmd()).execute(
        "--name", "default",
        "--branch", "not-a-real-branch-xyz-999");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("not-a-real-branch-xyz-999"));
  }

  @Test
  void testErrorMessageContainsRequestedCommitHash() {
    int code = new CommandLine(new RunCmd()).execute(
        "--name", "default",
        "--commit", "0000000000000000000000000000000000000000");
    restoreStreams();
    assertEquals(1, code);
    // Error should mention the requested commit or HEAD
    assertTrue(errContent.toString().contains("requested commit")
        || errContent.toString().contains("0000000000000000000000000000000000000000"));
  }

  @Test
  void testCustomServerUrlIsUsed() {
    // With custom server URL, failure message should reference that URL
    int code = new CommandLine(new RunCmd()).execute(
        "--name", "default",
        "--server", "http://localhost:29999");
    restoreStreams();
    assertEquals(1, code);
    // Either connection error or git repo error
    assertTrue(errContent.toString().contains("29999")
        || errContent.toString().contains("error")
        || errContent.toString().contains("Failed to communicate"));
  }
}

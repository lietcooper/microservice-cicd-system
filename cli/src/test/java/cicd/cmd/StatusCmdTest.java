package cicd.cmd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** Tests for StatusCmd validation logic and option parsing. */
class StatusCmdTest {

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

  // ── Validation: mutual exclusivity ──────────────────────────────────────────

  @Test
  void repoAndFileMutuallyExclusive() {
    int code = new CommandLine(new StatusCmd()).execute(
        "--repo", "https://example.com/repo.git",
        "--file", "some.yaml");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("mutually exclusive"));
  }

  @Test
  void mustProvideRepoOrFile() {
    int code = new CommandLine(new StatusCmd()).execute();
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("must provide either --repo or --file"));
  }

  @Test
  void runRequiresFile() {
    int code = new CommandLine(new StatusCmd()).execute(
        "--repo", "https://example.com/repo.git",
        "--run", "1");
    restoreStreams();
    assertEquals(1, code);
    // --repo and --run with --repo triggers mutual exclusivity or --run requires --file
    String err = errContent.toString();
    assertTrue(err.contains("mutually exclusive") || err.contains("--run requires --file"));
  }

  @Test
  void runAloneRequiresFile() {
    int code = new CommandLine(new StatusCmd()).execute(
        "--run", "1");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("must provide either --repo or --file"));
  }

  // ── File not found ──────────────────────────────────────────────────────────

  @Test
  void fileNotFoundReturnsError() {
    int code = new CommandLine(new StatusCmd()).execute(
        "--file", "nonexistent.yaml",
        "--run", "1");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("file not found"));
  }

  // ── Invalid YAML ───────────────────────────────────────────────────────────

  @Test
  void invalidYamlReturnsError(@TempDir File tempDir) throws Exception {
    File yamlFile = new File(tempDir, "bad.yaml");
    try (FileWriter w = new FileWriter(yamlFile)) {
      w.write("not: a: valid: pipeline");
    }
    int code = new CommandLine(new StatusCmd()).execute(
        "--file", yamlFile.getAbsolutePath(),
        "--run", "1");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("invalid YAML")
        || errContent.toString().contains("missing pipeline name"));
  }

  @Test
  void yamlWithoutPipelineNameReturnsError(@TempDir File tempDir) throws Exception {
    File yamlFile = new File(tempDir, "nopipe.yaml");
    try (FileWriter w = new FileWriter(yamlFile)) {
      w.write("stages:\n  - build\n");
    }
    int code = new CommandLine(new StatusCmd()).execute(
        "--file", yamlFile.getAbsolutePath(),
        "--run", "1");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("invalid YAML")
        || errContent.toString().contains("missing pipeline name"));
  }

  // ── File requires --run ────────────────────────────────────────────────────

  @Test
  void fileWithoutRunReturnsError(@TempDir File tempDir) throws Exception {
    File yamlFile = new File(tempDir, "valid.yaml");
    try (FileWriter w = new FileWriter(yamlFile)) {
      w.write("pipeline:\n  name: default\nstages:\n  - build\ncompile:\n"
          + "  stage: build\n  image: alpine\n  script: echo hi\n");
    }
    int code = new CommandLine(new StatusCmd()).execute(
        "--file", yamlFile.getAbsolutePath());
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("--run is required with --file"));
  }

  // ── Server connection failures ──────────────────────────────────────────────

  @Test
  void repoQueryFailsWhenServerUnreachable() {
    int code = new CommandLine(new StatusCmd()).execute(
        "--repo", "https://example.com/repo.git",
        "--server", "http://localhost:19999");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("could not connect to server"));
  }

  @Test
  void fileQueryFailsWhenServerUnreachable(@TempDir File tempDir) throws Exception {
    File yamlFile = new File(tempDir, "valid.yaml");
    try (FileWriter w = new FileWriter(yamlFile)) {
      w.write("pipeline:\n  name: default\nstages:\n  - build\ncompile:\n"
          + "  stage: build\n  image: alpine\n  script: echo hi\n");
    }
    int code = new CommandLine(new StatusCmd()).execute(
        "--file", yamlFile.getAbsolutePath(),
        "--run", "1",
        "--server", "http://localhost:19999");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("could not connect to server"));
  }

  // ── Edge cases ──────────────────────────────────────────────────────────────

  @Test
  void shortFileOptionWorks(@TempDir File tempDir) throws Exception {
    File yamlFile = new File(tempDir, "valid.yaml");
    try (FileWriter w = new FileWriter(yamlFile)) {
      w.write("pipeline:\n  name: default\nstages:\n  - build\ncompile:\n"
          + "  stage: build\n  image: alpine\n  script: echo hi\n");
    }
    int code = new CommandLine(new StatusCmd()).execute(
        "-f", yamlFile.getAbsolutePath(),
        "--run", "1",
        "--server", "http://localhost:19999");
    restoreStreams();
    assertEquals(1, code);
    // Should fail at connection, not at option parsing
    assertTrue(errContent.toString().contains("could not connect to server"));
  }

  @Test
  void negativeRunNumberAttemptsMakingRequest(@TempDir File tempDir) throws Exception {
    File yamlFile = new File(tempDir, "valid.yaml");
    try (FileWriter w = new FileWriter(yamlFile)) {
      w.write("pipeline:\n  name: default\nstages:\n  - build\ncompile:\n"
          + "  stage: build\n  image: alpine\n  script: echo hi\n");
    }
    int code = new CommandLine(new StatusCmd()).execute(
        "--file", yamlFile.getAbsolutePath(),
        "--run", "-1",
        "--server", "http://localhost:19999");
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("could not connect to server")
        || errContent.toString().contains("error"));
  }
}

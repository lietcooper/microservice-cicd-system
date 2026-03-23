package cicd.cmd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintStream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * Integration tests for StatusCmd using MockWebServer to simulate
 * the server's HTTP responses and verify the full CLI flow.
 */
class StatusCmdIntegrationTest {

  private MockWebServer server;
  private String serverUrl;
  private ByteArrayOutputStream outContent;
  private ByteArrayOutputStream errContent;
  private PrintStream originalOut;
  private PrintStream originalErr;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    serverUrl = server.url("/").toString();
    // Remove trailing slash
    if (serverUrl.endsWith("/")) {
      serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
    }

    outContent = new ByteArrayOutputStream();
    errContent = new ByteArrayOutputStream();
    originalOut = System.out;
    originalErr = System.err;
    System.setOut(new PrintStream(outContent));
    System.setErr(new PrintStream(errContent));
  }

  @AfterEach
  void tearDown() throws Exception {
    System.setOut(originalOut);
    System.setErr(originalErr);
    server.shutdown();
  }

  // ── Repo query: all runs ───────────────────────────────────────────────────

  @Test
  void repoQueryReturnsAllRuns() {
    server.enqueue(new MockResponse()
        .addHeader("Content-Type", "application/json")
        .setBody("[{"
            + "\"pipeline-name\":\"default\","
            + "\"run-no\":1,"
            + "\"status\":\"Success\","
            + "\"git-repo\":\"https://github.com/example/repo.git\","
            + "\"git-branch\":\"main\","
            + "\"git-hash\":\"abc1234\","
            + "\"start\":\"2025-08-29T16:17:52-07:00\","
            + "\"end\":\"2025-08-29T16:21:32-07:00\","
            + "\"stages\":[]"
            + "},{"
            + "\"pipeline-name\":\"default\","
            + "\"run-no\":2,"
            + "\"status\":\"Failed\","
            + "\"git-repo\":\"https://github.com/example/repo.git\","
            + "\"git-branch\":\"main\","
            + "\"git-hash\":\"def5678\","
            + "\"start\":\"2025-08-29T16:42:52-07:00\","
            + "\"end\":\"2025-08-29T16:43:14-07:00\","
            + "\"stages\":[]"
            + "}]"));

    int code = new CommandLine(new StatusCmd()).execute(
        "--repo", "https://github.com/example/repo.git",
        "--server", serverUrl);

    assertEquals(0, code);
    String out = outContent.toString();
    assertTrue(out.contains("pipeline: default"));
    assertTrue(out.contains("run-no: 1"));
    assertTrue(out.contains("status: Success"));
    assertTrue(out.contains("run-no: 2"));
    assertTrue(out.contains("status: Failed"));
    assertTrue(out.contains("git-branch: main"));
  }

  @Test
  void repoQueryEmptyArrayPrintsNoRuns() {
    server.enqueue(new MockResponse()
        .addHeader("Content-Type", "application/json")
        .setBody("[]"));

    int code = new CommandLine(new StatusCmd()).execute(
        "--repo", "https://github.com/example/repo.git",
        "--server", serverUrl);

    assertEquals(0, code);
    // Empty array, nothing printed (no runs in array)
  }

  // ── Specific run: detailed status with stages and jobs ─────────────────────

  @Test
  void fileRunQueryReturnsDetailedStatus(@TempDir File tempDir) throws Exception {
    File yamlFile = createValidYaml(tempDir);

    server.enqueue(new MockResponse()
        .addHeader("Content-Type", "application/json")
        .setBody("{"
            + "\"pipeline-name\":\"default\","
            + "\"run-no\":1,"
            + "\"status\":\"Success\","
            + "\"git-repo\":\"https://github.com/example/repo.git\","
            + "\"git-branch\":\"main\","
            + "\"git-hash\":\"abc1234\","
            + "\"start\":\"2025-08-29T16:17:52-07:00\","
            + "\"end\":\"2025-08-29T16:21:32-07:00\","
            + "\"stages\":[{"
            + "  \"name\":\"build\","
            + "  \"status\":\"Success\","
            + "  \"start\":\"2025-08-29T16:18:05-07:00\","
            + "  \"end\":\"2025-08-29T16:19:32-07:00\","
            + "  \"jobs\":[{"
            + "    \"name\":\"compile\","
            + "    \"status\":\"Success\","
            + "    \"start\":\"2025-08-29T16:18:05-07:00\","
            + "    \"end\":\"2025-08-29T16:19:32-07:00\""
            + "  }]"
            + "},{"
            + "  \"name\":\"test\","
            + "  \"status\":\"Running\","
            + "  \"start\":\"2025-08-29T16:20:23-07:00\","
            + "  \"end\":null,"
            + "  \"jobs\":[{"
            + "    \"name\":\"unittests\","
            + "    \"status\":\"Success\","
            + "    \"start\":\"2025-08-29T16:20:23-07:00\","
            + "    \"end\":\"2025-08-29T16:21:32-07:00\""
            + "  },{"
            + "    \"name\":\"reports\","
            + "    \"status\":\"Running\","
            + "    \"start\":\"2025-08-29T16:21:00-07:00\","
            + "    \"end\":null"
            + "  }]"
            + "},{"
            + "  \"name\":\"docs\","
            + "  \"status\":\"Pending\","
            + "  \"start\":null,"
            + "  \"end\":null,"
            + "  \"jobs\":[{"
            + "    \"name\":\"javadoc\","
            + "    \"status\":\"Pending\","
            + "    \"start\":null,"
            + "    \"end\":null"
            + "  }]"
            + "}]"
            + "}"));

    int code = new CommandLine(new StatusCmd()).execute(
        "--file", yamlFile.getAbsolutePath(),
        "--run", "1",
        "--server", serverUrl);

    assertEquals(0, code);
    String out = outContent.toString();

    // Verify stage/job output format
    assertTrue(out.contains("build:"));
    assertTrue(out.contains("    status: Success"));
    assertTrue(out.contains("    compile:"));
    assertTrue(out.contains("        status: Success"));

    assertTrue(out.contains("test:"));
    assertTrue(out.contains("    status: Running"));
    assertTrue(out.contains("    unittests:"));
    assertTrue(out.contains("    reports:"));
    assertTrue(out.contains("        status: Running"));

    assertTrue(out.contains("docs:"));
    assertTrue(out.contains("    status: Pending"));
    assertTrue(out.contains("    javadoc:"));
    assertTrue(out.contains("        status: Pending"));
  }

  @Test
  void completedPipelineShowsAllSuccess(@TempDir File tempDir) throws Exception {
    File yamlFile = createValidYaml(tempDir);

    server.enqueue(new MockResponse()
        .addHeader("Content-Type", "application/json")
        .setBody("{"
            + "\"pipeline-name\":\"default\","
            + "\"run-no\":3,"
            + "\"status\":\"Success\","
            + "\"git-repo\":\"https://github.com/example/repo.git\","
            + "\"git-branch\":\"main\","
            + "\"git-hash\":\"abc1234\","
            + "\"start\":\"2025-08-29T17:03:52-07:00\","
            + "\"end\":\"2025-08-29T17:05:32-07:00\","
            + "\"stages\":[{"
            + "  \"name\":\"build\","
            + "  \"status\":\"Success\","
            + "  \"start\":\"2025-08-29T17:03:52-07:00\","
            + "  \"end\":\"2025-08-29T17:04:30-07:00\","
            + "  \"jobs\":[{"
            + "    \"name\":\"compile\","
            + "    \"status\":\"Success\","
            + "    \"start\":\"2025-08-29T17:03:52-07:00\","
            + "    \"end\":\"2025-08-29T17:04:30-07:00\""
            + "  }]"
            + "}]"
            + "}"));

    int code = new CommandLine(new StatusCmd()).execute(
        "--file", yamlFile.getAbsolutePath(),
        "--run", "3",
        "--server", serverUrl);

    assertEquals(0, code);
    String out = outContent.toString();
    assertTrue(out.contains("build:"));
    assertTrue(out.contains("    status: Success"));
    assertTrue(out.contains("    compile:"));
    assertTrue(out.contains("        status: Success"));
  }

  // ── Error responses from server ────────────────────────────────────────────

  @Test
  void server404ReturnsRunNotFound(@TempDir File tempDir) throws Exception {
    File yamlFile = createValidYaml(tempDir);

    server.enqueue(new MockResponse()
        .setResponseCode(404)
        .setBody("Not found"));

    int code = new CommandLine(new StatusCmd()).execute(
        "--file", yamlFile.getAbsolutePath(),
        "--run", "999",
        "--server", serverUrl);

    assertEquals(1, code);
    assertTrue(errContent.toString().contains("pipeline run not found"));
  }

  @Test
  void server500ReturnsError(@TempDir File tempDir) throws Exception {
    File yamlFile = createValidYaml(tempDir);

    server.enqueue(new MockResponse()
        .setResponseCode(500)
        .setBody("Internal Server Error"));

    int code = new CommandLine(new StatusCmd()).execute(
        "--file", yamlFile.getAbsolutePath(),
        "--run", "1",
        "--server", serverUrl);

    assertEquals(1, code);
    assertTrue(errContent.toString().contains("server returned 500"));
  }

  @Test
  void repoQuery404ReturnsError() {
    server.enqueue(new MockResponse()
        .setResponseCode(404)
        .setBody("No pipelines found"));

    int code = new CommandLine(new StatusCmd()).execute(
        "--repo", "https://github.com/example/nonexistent.git",
        "--server", serverUrl);

    assertEquals(1, code);
    assertTrue(errContent.toString().contains("not found"));
  }

  @Test
  void repoQuery500ReturnsError() {
    server.enqueue(new MockResponse()
        .setResponseCode(500)
        .setBody("Internal Server Error"));

    int code = new CommandLine(new StatusCmd()).execute(
        "--repo", "https://github.com/example/repo.git",
        "--server", serverUrl);

    assertEquals(1, code);
    assertTrue(errContent.toString().contains("server returned 500"));
  }

  // ── Verify correct endpoint is called ──────────────────────────────────────

  @Test
  void repoQueryCallsCorrectEndpoint() throws Exception {
    server.enqueue(new MockResponse()
        .addHeader("Content-Type", "application/json")
        .setBody("[]"));

    int code = new CommandLine(new StatusCmd()).execute(
        "--repo", "https://github.com/example/repo.git",
        "--server", serverUrl);

    assertEquals(0, code);
    var request = server.takeRequest();
    assertTrue(request.getPath().startsWith("/pipelines/status?repo="));
  }

  @Test
  void fileRunQueryCallsCorrectEndpoint(@TempDir File tempDir) throws Exception {
    File yamlFile = createValidYaml(tempDir);

    server.enqueue(new MockResponse()
        .addHeader("Content-Type", "application/json")
        .setBody("{\"pipeline-name\":\"default\",\"run-no\":1,"
            + "\"status\":\"Success\",\"stages\":[]}"));

    int code = new CommandLine(new StatusCmd()).execute(
        "--file", yamlFile.getAbsolutePath(),
        "--run", "1",
        "--server", serverUrl);

    assertEquals(0, code);
    var request = server.takeRequest();
    assertEquals("/pipelines/default/status?run=1", request.getPath());
  }

  // ── Failed pipeline status ─────────────────────────────────────────────────

  @Test
  void failedPipelineShowsFailedStatus(@TempDir File tempDir) throws Exception {
    File yamlFile = createValidYaml(tempDir);

    server.enqueue(new MockResponse()
        .addHeader("Content-Type", "application/json")
        .setBody("{"
            + "\"pipeline-name\":\"default\","
            + "\"run-no\":2,"
            + "\"status\":\"Failed\","
            + "\"git-repo\":\"https://github.com/example/repo.git\","
            + "\"git-branch\":\"main\","
            + "\"git-hash\":\"def5678\","
            + "\"start\":\"2025-08-29T16:42:52-07:00\","
            + "\"end\":\"2025-08-29T16:43:14-07:00\","
            + "\"stages\":[{"
            + "  \"name\":\"build\","
            + "  \"status\":\"Failed\","
            + "  \"start\":\"2025-08-29T16:42:52-07:00\","
            + "  \"end\":\"2025-08-29T16:43:14-07:00\","
            + "  \"jobs\":[{"
            + "    \"name\":\"compile\","
            + "    \"status\":\"Failed\","
            + "    \"start\":\"2025-08-29T16:42:52-07:00\","
            + "    \"end\":\"2025-08-29T16:43:14-07:00\""
            + "  }]"
            + "}]"
            + "}"));

    int code = new CommandLine(new StatusCmd()).execute(
        "--file", yamlFile.getAbsolutePath(),
        "--run", "2",
        "--server", serverUrl);

    assertEquals(0, code);
    String out = outContent.toString();
    assertTrue(out.contains("build:"));
    assertTrue(out.contains("    status: Failed"));
    assertTrue(out.contains("    compile:"));
    assertTrue(out.contains("        status: Failed"));
  }

  // ── Helper ─────────────────────────────────────────────────────────────────

  private File createValidYaml(File dir) throws Exception {
    File yamlFile = new File(dir, "ci.yaml");
    try (FileWriter w = new FileWriter(yamlFile)) {
      w.write("pipeline:\n  name: default\nstages:\n  - build\n"
          + "  - test\n  - docs\ncompile:\n  stage: build\n"
          + "  image: alpine\n  script: echo hi\n");
    }
    return yamlFile;
  }
}

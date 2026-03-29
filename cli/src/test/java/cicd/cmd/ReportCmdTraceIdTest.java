package cicd.cmd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/**
 * Tests that ReportCmd output includes trace-id field
 * at both level-1 (all runs) and level-2+ (run detail) views.
 */
class ReportCmdTraceIdTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private MockWebServer server;
  private ByteArrayOutputStream outContent;
  private ByteArrayOutputStream errContent;
  private PrintStream originalOut;
  private PrintStream originalErr;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
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

  @Test
  void runDetailOutputIncludesTraceId() throws Exception {
    ObjectNode json = MAPPER.createObjectNode();
    json.put("pipelineName", "my-pipe");
    json.put("run-no", 1);
    json.put("status", "SUCCESS");
    json.put("trace-id", "0af7651916cd43dd8448eb211c80319c");
    json.put("git-repo", "https://github.com/org/repo");
    json.put("git-branch", "main");
    json.put("git-hash", "abc123");
    json.putNull("start");
    json.putNull("end");
    json.putArray("stages");

    server.enqueue(new MockResponse()
        .setBody(json.toString())
        .addHeader("Content-Type", "application/json"));

    String url = server.url("/").toString();
    // Remove trailing slash
    url = url.substring(0, url.length() - 1);

    int code = new CommandLine(new ReportCmd()).execute(
        "--pipeline", "my-pipe",
        "--run", "1",
        "--server", url);

    System.setOut(originalOut);
    assertEquals(0, code);

    String output = outContent.toString();
    assertTrue(output.contains("trace-id: 0af7651916cd43dd8448eb211c80319c"),
        "Output should contain trace-id line. Got: " + output);
  }

  @Test
  void level1OutputIncludesTraceId() throws Exception {
    ObjectNode json = MAPPER.createObjectNode();
    json.put("pipelineName", "my-pipe");

    ArrayNode runs = json.putArray("runs");
    ObjectNode run = runs.addObject();
    run.put("run-no", 1);
    run.put("status", "SUCCESS");
    run.put("trace-id", "deadbeef12345678aabbccdd11223344");
    run.put("git-branch", "main");
    run.put("git-hash", "abc123");
    run.putNull("start");
    run.putNull("end");

    server.enqueue(new MockResponse()
        .setBody(json.toString())
        .addHeader("Content-Type", "application/json"));

    String url = server.url("/").toString();
    url = url.substring(0, url.length() - 1);

    int code = new CommandLine(new ReportCmd()).execute(
        "--pipeline", "my-pipe",
        "--server", url);

    System.setOut(originalOut);
    assertEquals(0, code);

    String output = outContent.toString();
    assertTrue(
        output.contains("trace-id: deadbeef12345678aabbccdd11223344"),
        "Level-1 output should contain trace-id. Got: " + output);
  }

  @Test
  void runDetailShowsNullWhenTraceIdMissing() throws Exception {
    ObjectNode json = MAPPER.createObjectNode();
    json.put("pipelineName", "my-pipe");
    json.put("run-no", 1);
    json.put("status", "SUCCESS");
    // trace-id not present
    json.put("git-repo", "https://github.com/org/repo");
    json.put("git-branch", "main");
    json.put("git-hash", "abc123");
    json.putNull("start");
    json.putNull("end");
    json.putArray("stages");

    server.enqueue(new MockResponse()
        .setBody(json.toString())
        .addHeader("Content-Type", "application/json"));

    String url = server.url("/").toString();
    url = url.substring(0, url.length() - 1);

    int code = new CommandLine(new ReportCmd()).execute(
        "--pipeline", "my-pipe",
        "--run", "1",
        "--server", url);

    System.setOut(originalOut);
    assertEquals(0, code);

    String output = outContent.toString();
    assertTrue(output.contains("trace-id: null"),
        "Output should show trace-id: null when missing. Got: " + output);
  }
}

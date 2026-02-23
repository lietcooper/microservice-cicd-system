package cicd.cmd;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "report",
    description = "Query pipeline execution history"
)
public class ReportCmd implements Callable<Integer> {

  @Option(names = "--pipeline", description = "Pipeline name", required = true)
  private String pipeline;

  @Option(names = "--run", description = "Run number")
  private Integer run;

  @Option(names = "--stage", description = "Stage name (requires --run)")
  private String stage;

  @Option(names = "--job", description = "Job name (requires --stage)")
  private String job;

  @Option(
      names = "--server",
      description = "Server URL",
      defaultValue = "http://localhost:8080")
  private String serverUrl;

  private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .build();

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public Integer call() {
    // Validate option combinations
    if (job != null && stage == null) {
      System.err.println("error: --job requires --stage");
      return 1;
    }
    if (stage != null && run == null) {
      System.err.println("error: --stage requires --run");
      return 1;
    }

    // Build URL based on which options were provided
    String url = buildUrl();

    // Make HTTP GET request
    Request request = new Request.Builder().url(url).get().build();

    try (Response response = CLIENT.newCall(request).execute()) {
      String body = response.body().string();

      if (response.code() == 404) {
        System.err.println("error: not found - " + body);
        return 1;
      }
      if (!response.isSuccessful()) {
        System.err.println("error: server returned " + response.code() + " - " + body);
        return 1;
      }

      // Parse and print
      JsonNode json = MAPPER.readTree(body);
      printReport(json);
      return 0;

    } catch (IOException e) {
      System.err.println("error: could not connect to server at " + serverUrl);
      System.err.println("Make sure the server is running.");
      return 1;
    }
  }

  /** Builds the correct endpoint URL based on provided options. */
  private String buildUrl() {
    StringBuilder sb = new StringBuilder(serverUrl)
        .append("/pipelines/")
        .append(pipeline)
        .append("/runs");

    if (run != null) {
      sb.append("/").append(run);
      if (stage != null) {
        sb.append("/stages/").append(stage);
        if (job != null) {
          sb.append("/jobs/").append(job);
        }
      }
    }
    return sb.toString();
  }

  /** Prints the JSON response in a YAML-like format based on the level. */
  private void printReport(JsonNode json) {
    if (run == null) {
      // Level 1: all runs for a pipeline
      printLevel1(json);
    } else {
      // Level 2, 3, 4: run detail (same response shape)
      printRunDetail(json);
    }
  }

  // ── Level 1 ────────────────────────────────────────────────────────────────

  private void printLevel1(JsonNode json) {
    System.out.println("pipeline: " + text(json, "pipelineName"));
    System.out.println("runs:");

    JsonNode runs = json.path("runs");
    if (runs.isMissingNode() || !runs.isArray() || runs.size() == 0) {
      System.out.println("  (no runs found)");
      return;
    }

    for (JsonNode r : runs) {
      System.out.println("  - run-no: " + r.path("run-no").asInt());
      System.out.println("    status: " + text(r, "status"));
      System.out.println("    git-branch: " + text(r, "git-branch"));
      System.out.println("    git-hash: " + text(r, "git-hash"));
      System.out.println("    start: " + text(r, "start"));
      System.out.println("    end: " + text(r, "end"));
    }
  }

  // ── Level 2 / 3 / 4 ────────────────────────────────────────────────────────

  private void printRunDetail(JsonNode json) {
    System.out.println("pipeline: " + text(json, "pipelineName"));
    System.out.println("run-no: " + json.path("run-no").asInt());
    System.out.println("status: " + text(json, "status"));
    System.out.println("git-repo: " + text(json, "git-repo"));
    System.out.println("git-branch: " + text(json, "git-branch"));
    System.out.println("git-hash: " + text(json, "git-hash"));
    System.out.println("start: " + text(json, "start"));
    System.out.println("end: " + text(json, "end"));

    JsonNode stages = json.path("stages");
    if (stages.isMissingNode() || !stages.isArray()) {
      return;
    }

    System.out.println("stages:");
    for (JsonNode s : stages) {
      System.out.println("  - name: " + text(s, "name"));
      System.out.println("    status: " + text(s, "status"));
      System.out.println("    start: " + text(s, "start"));
      System.out.println("    end: " + text(s, "end"));

      JsonNode jobs = s.path("jobs");
      if (!jobs.isMissingNode() && jobs.isArray()) {
        System.out.println("    jobs:");
        for (JsonNode j : jobs) {
          System.out.println("      - name: " + text(j, "name"));
          System.out.println("        status: " + text(j, "status"));
          System.out.println("        start: " + text(j, "start"));
          System.out.println("        end: " + text(j, "end"));
        }
      }
    }
  }

  /** Returns the text value of a JSON field, or "null" if missing. */
  private static String text(JsonNode node, String field) {
    JsonNode v = node.path(field);
    return v.isMissingNode() || v.isNull() ? "null" : v.asText();
  }
}

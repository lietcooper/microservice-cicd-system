package cicd.cmd;

import cicd.model.Pipeline;
import cicd.parser.YamlParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Queries and displays pipeline run status from the server. */
@Command(
    name = "status",
    description = "Query pipeline run status"
)
public class StatusCmd implements Callable<Integer> {

  @Option(names = "--repo", description = "Repository URL")
  private String repo;

  @Option(names = "--run", description = "Run number (requires --file)")
  private Integer run;

  @Option(names = {"-f", "--file"}, description = "Pipeline file path")
  private String file;

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
    if (repo != null && file != null) {
      System.err.println("error: --repo and --file are mutually exclusive");
      return 1;
    }
    if (repo == null && file == null) {
      System.err.println("error: must provide either --repo or --file");
      return 1;
    }
    if (run != null && file == null) {
      System.err.println("error: --run requires --file");
      return 1;
    }

    if (repo != null) {
      return handleRepoQuery();
    } else {
      return handleFileQuery();
    }
  }

  private int handleRepoQuery() {
    String url = serverUrl + "/pipelines/status?repo=" + repo;
    Request request = new Request.Builder().url(url).get().build();

    try (Response response = CLIENT.newCall(request).execute()) {
      String body = response.body().string();

      if (response.code() == 404) {
        System.err.println("error: not found - " + body);
        return 1;
      }
      if (!response.isSuccessful()) {
        System.err.println(
            "error: server returned " + response.code() + " - " + body);
        return 1;
      }

      JsonNode json = MAPPER.readTree(body);
      printRepoStatus(json);
      return 0;

    } catch (IOException ex) {
      System.err.println(
          "error: could not connect to server at " + serverUrl);
      System.err.println("Make sure the server is running.");
      return 1;
    }
  }

  private int handleFileQuery() {
    File f = new File(file);
    if (!f.exists()) {
      System.err.println("error: file not found: " + file);
      return 1;
    }

    YamlParser parser = new YamlParser(f.getPath());
    Pipeline pipeline = parser.parse();
    if (pipeline == null || pipeline.name == null) {
      System.err.println("error: invalid YAML or missing pipeline name in "
          + file);
      for (String err : parser.getErrors()) {
        System.err.println(err);
      }
      return 1;
    }

    String pipelineName = pipeline.name;
    String url;
    if (run != null) {
      url = serverUrl + "/pipelines/" + pipelineName + "/status?run=" + run;
    } else {
      System.err.println("error: --run is required with --file");
      return 1;
    }

    Request request = new Request.Builder().url(url).get().build();

    try (Response response = CLIENT.newCall(request).execute()) {
      String body = response.body().string();

      if (response.code() == 404) {
        System.err.println("error: pipeline run not found");
        return 1;
      }
      if (!response.isSuccessful()) {
        System.err.println(
            "error: server returned " + response.code() + " - " + body);
        return 1;
      }

      JsonNode json = MAPPER.readTree(body);
      printRunStatus(json);
      return 0;

    } catch (IOException ex) {
      System.err.println(
          "error: could not connect to server at " + serverUrl);
      System.err.println("Make sure the server is running.");
      return 1;
    }
  }

  private void printRepoStatus(JsonNode json) {
    if (!json.isArray() || json.isEmpty()) {
      System.out.println("(no runs found)");
      return;
    }
    for (JsonNode run : json) {
      System.out.println("pipeline: " + text(run, "pipeline-name"));
      System.out.println("  run-no: " + run.path("run-no").asInt());
      System.out.println("  status: " + text(run, "status"));
      System.out.println("  git-branch: " + text(run, "git-branch"));
      System.out.println("  git-hash: " + text(run, "git-hash"));
      printStagesAndJobs(run, "  ");
    }
  }

  private void printRunStatus(JsonNode json) {
    printStagesAndJobs(json, "");
  }

  private void printStagesAndJobs(JsonNode json, String indent) {
    JsonNode stages = json.path("stages");
    if (stages.isMissingNode() || !stages.isArray() || stages.isEmpty()) {
      return;
    }

    for (JsonNode stage : stages) {
      System.out.println(indent + text(stage, "name") + ":");
      System.out.println(indent + "    status: " + text(stage, "status"));

      JsonNode jobs = stage.path("jobs");
      if (!jobs.isMissingNode() && jobs.isArray()) {
        for (JsonNode job : jobs) {
          System.out.println(indent + "    " + text(job, "name") + ":");
          System.out.println(indent + "        status: "
              + text(job, "status"));
        }
      }
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode val = node.path(field);
    return val.isMissingNode() || val.isNull() ? "null" : val.asText();
  }
}

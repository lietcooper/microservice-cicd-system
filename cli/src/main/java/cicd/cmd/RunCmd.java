package cicd.cmd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import cicd.util.GitHelper;

@Command(
    name = "run",
    description = "Execute a CI/CD pipeline via REST API"
)
public class RunCmd implements Callable<Integer> {

    @Option(names = "--name", description = "Pipeline name to find in repository")
    private String name;

    @Option(names = "--file", description = "Path to local pipeline YAML file to execute")
    private String file;

    @Option(names = "--repo-url", description = "Repository URL (default: auto-detected from current directory)")
    private String repoUrl;

    @Option(names = "--branch", description = "Git branch (optional)")
    private String branch;

    @Option(names = "--commit", description = "Git commit (optional)")
    private String commit;

    @Option(names = "--server", description = "Server URL", defaultValue = "http://localhost:8080")
    private String serverUrl;

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

  @Override
  public Integer call() {
        String currentPath = System.getProperty("user.dir");

        // Validate options
        if (name == null && file == null) {
            System.err.println("error: must specify either --name or --file");
            return 1;
        }
        if (name != null && file != null) {
            System.err.println("error: --name and --file are mutually exclusive");
            return 1;
        }

        if (repoUrl == null) {
            repoUrl = detectRepoUrl(currentPath);
            if (repoUrl == null || repoUrl.isBlank()) {
                System.err.println("error: --repo-url is required or the current git repository must have an origin remote");
                return 1;
            }
        }

        try {
            // Build the request
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode requestBody = mapper.createObjectNode();
            requestBody.put("repoUrl", repoUrl);

            if (branch != null) {
                requestBody.put("branch", branch);
            }
            if (commit != null) {
                requestBody.put("commit", commit);
            }

            if (name != null) {
                requestBody.put("pipelineName", name);
            } else {
                // Read YAML file content locally and upload
                File yamlFile = new File(file);
                if (!yamlFile.exists()) {
                    System.err.println("error: file not found: " + file);
                    return 1;
                }
                String yamlContent = Files.readString(yamlFile.toPath());
                requestBody.put("pipelineYaml", yamlContent);
            }

            // Make HTTP request
            Request request = new Request.Builder()
                    .url(serverUrl + "/api/pipelines/execute")
                    .post(RequestBody.create(requestBody.toString(), JSON))
                    .build();

            System.out.println("Sending pipeline execution request to server...");

            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body().string();

                if (response.code() == 202) {
                    // Async: pipeline queued for execution
                    ObjectNode responseJson = (ObjectNode) mapper.readTree(responseBody);
                    String pipelineName = responseJson.get("pipelineName").asText();
                    int runNo = responseJson.get("runNo").asInt();

                    System.out.println("\nPipeline execution queued.");
                    System.out.println("  Pipeline: " + pipelineName);
                    System.out.println("  Run #: " + runNo);
                    System.out.println("  Status: PENDING");
                    System.out.println("\nCheck progress with:");
                    System.out.println("  cicd report --pipeline "
                        + pipelineName + " --run " + runNo);
                    return 0;
                } else if (response.isSuccessful()) {
                    // Synchronous: pipeline completed (legacy support)
                    ObjectNode responseJson = (ObjectNode) mapper.readTree(responseBody);

                    System.out.println("\n=== Pipeline Execution Result ===");
                    System.out.println("Pipeline: " + responseJson.get("pipelineName").asText());
                    System.out.println("Run #: " + responseJson.get("runNo").asInt());
                    System.out.println("Status: " + responseJson.get("status").asText());
                    System.out.println("Git Branch: " + responseJson.get("gitBranch").asText());
                    System.out.println("Git Commit: " + responseJson.get("gitHash").asText());

                    if (responseJson.has("message")) {
                        System.out.println("Message: " + responseJson.get("message").asText());
                    }

                    String status = responseJson.get("status").asText();
                    return "SUCCESS".equals(status) ? 0 : 1;
                } else {
                    System.err.println("Server error: " + response.code() + " " + response.message());
                    System.err.println("Details: " + responseBody);
                    return 1;
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to communicate with server: " + e.getMessage());
            System.err.println("Make sure the server is running at " + serverUrl);
            return 1;
        }
    }

    private String detectRepoUrl(String currentPath) {
        if (!GitHelper.isGitRoot(currentPath)) {
            return null;
        }
        try {
            return GitHelper.remoteOriginUrl(currentPath);
        } catch (RuntimeException e) {
            return null;
        }
    }
}

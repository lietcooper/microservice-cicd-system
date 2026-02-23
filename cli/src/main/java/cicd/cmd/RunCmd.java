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

    @Option(names = "--name", description = "Pipeline name to run")
    private String name;

    @Option(names = "--file", description = "Path to pipeline YAML file")
    private String file;

    @Option(names = "--branch", description = "Git branch (default: current)")
    private String branch;

    @Option(names = "--commit", description = "Git commit (default: HEAD)")
    private String commit;

    @Option(names = "--server", description = "Server URL", defaultValue = "http://localhost:8080")
    private String serverUrl;

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS) // 10 minutes for long-running pipelines
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    public Integer call() {
        String repoPath = System.getProperty("user.dir");

        // Validate git repository
        if (!GitHelper.isGitRoot(repoPath)) {
            System.err.println("error: not in a git repository root directory");
            return 1;
        }

        // Validate options
        if (name == null && file == null) {
            System.err.println("error: must specify either --name or --file");
            return 1;
        }
        if (name != null && file != null) {
            System.err.println("error: --name and --file are mutually exclusive");
            return 1;
        }

        // Validate branch if specified
        if (branch != null) {
            String cur = GitHelper.currentBranch(repoPath);
            if (!branch.equals(cur)) {
                System.err.println("error: requested branch '" + branch
                    + "' but currently on '" + cur + "'");
                return 1;
            }
        }

        // Validate commit if specified
        if (commit != null) {
            String full = GitHelper.currentCommitFull(repoPath);
            String shortHash = GitHelper.currentCommit(repoPath);
            if (!commit.equals(full) && !commit.equals(shortHash)) {
                System.err.println("error: requested commit '" + commit
                    + "' but HEAD is at '" + shortHash + "'");
                return 1;
            }
        }

        try {
            // Build the request
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode requestBody = mapper.createObjectNode();
            requestBody.put("repoPath", repoPath);

            if (branch != null) {
                requestBody.put("branch", branch);
            }
            if (commit != null) {
                requestBody.put("commit", commit);
            }

            if (name != null) {
                requestBody.put("pipelineName", name);
            } else {
                // Read YAML file content
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

                if (response.isSuccessful()) {
                    // Parse and display the response
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

                    // Return 0 for success, 1 for failed pipeline
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
}
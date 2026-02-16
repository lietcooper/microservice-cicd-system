package cicd.cmd;

import cicd.docker.DockerRunner;
import cicd.docker.LocalDockerRunner;
import cicd.executor.PipelineExecutor;
import cicd.model.Pipeline;
import cicd.parser.YamlParser;
import cicd.validator.Validator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name = "run",
    description = "Execute a CI/CD pipeline"
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

  private DockerRunner dockerRunner;

  public void setDockerRunner(DockerRunner runner) {
    this.dockerRunner = runner;
  }

  @Override
  public Integer call() {
    String repoPath = System.getProperty("user.dir");

    if (!GitHelper.isGitRoot(repoPath)) {
      System.err.println("error: not in a git repository root directory");
      return 1;
    }

    if (name == null && file == null) {
      System.err.println("error: must specify either --name or --file");
      return 1;
    }
    if (name != null && file != null) {
      System.err.println("error: --name and --file are mutually exclusive");
      return 1;
    }

    if (branch != null) {
      String cur = GitHelper.currentBranch(repoPath);
      if (!branch.equals(cur)) {
        System.err.println("error: requested branch '" + branch
            + "' but currently on '" + cur + "'");
        return 1;
      }
    }

    if (commit != null) {
      String full = GitHelper.currentCommitFull(repoPath);
      String shortHash = GitHelper.currentCommit(repoPath);
      if (!commit.equals(full) && !commit.equals(shortHash)) {
        System.err.println("error: requested commit '" + commit
            + "' but HEAD is at '" + shortHash + "'");
        return 1;
      }
    }

    Pipeline pipeline;
    if (name != null) {
      PipelineFinder.FindResult found = PipelineFinder.findByName(name);
      if (found.hasError()) {
        System.err.println("error: " + found.error);
        return 1;
      }
      pipeline = found.pipeline;
    } else {
      File f = new File(file);
      if (!f.exists()) {
        System.err.println("error: file not found: " + file);
        return 1;
      }
      YamlParser parser = new YamlParser(file);
      Pipeline p = parser.parse();
      if (!parser.getErrors().isEmpty()) {
        parser.getErrors().forEach(System.err::println);
        return 1;
      }
      Validator v = new Validator(file, p);
      List<String> vErrors = v.validate();
      if (!vErrors.isEmpty()) {
        vErrors.forEach(System.err::println);
        return 1;
      }
      pipeline = p;
    }

    DockerRunner runner = dockerRunner != null ? dockerRunner : new LocalDockerRunner();
    PipelineExecutor executor = new PipelineExecutor(runner, repoPath);
    boolean success = executor.execute(pipeline);
    return success ? 0 : 1;
  }
}
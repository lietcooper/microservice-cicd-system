package cicd.executor;

import cicd.docker.DockerRunner;
import cicd.model.Job;
import cicd.model.Pipeline;
import java.util.List;

public class PipelineExecutor {

  private final DockerRunner runner;
  private final String repoPath;

  public PipelineExecutor(DockerRunner runner, String repoPath) {
    this.runner = runner;
    this.repoPath = repoPath;
  }

  public boolean execute(Pipeline pipeline) {
    System.out.println("=== Running pipeline: " + pipeline.name + " ===");

    ExecutionPlanner planner = new ExecutionPlanner(pipeline);
    List<ExecutionPlanner.StageExecution> plan = planner.computeOrder();

    for (ExecutionPlanner.StageExecution stage : plan) {
      System.out.println("\n--- Stage: " + stage.getStageName() + " ---");

      for (Job job : stage.getJobs()) {
        System.out.println("  > Job: " + job.name + " [" + job.image + "]");

        JobResult result = runner.runJob(job.image, job.script, repoPath);

        if (result.output() != null && !result.output().isBlank()) {
          result.output().lines().forEach(l -> System.out.println("    " + l));
        }

        if (!result.ok()) {
          System.err.println("  x Job '" + job.name + "' failed (exit " + result.exitCode() + ")");
          System.err.println("=== Pipeline FAILED ===");
          return false;
        }
        System.out.println("  v Job '" + job.name + "' passed");
      }
    }

    System.out.println("\n=== Pipeline PASSED ===");
    return true;
  }
}
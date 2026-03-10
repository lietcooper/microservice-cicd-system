package cicd.cmd;

import cicd.executor.ExecutionPlanner;
import cicd.executor.ExecutionPlanner.StageExecution;
import cicd.model.Job;
import cicd.model.Pipeline;
import cicd.parser.YamlParser;
import cicd.validator.Validator;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/** Dry-run command that validates and prints execution order. */
@Command(
    name = "dryrun",
    description = "Dry-run a pipeline: validate and print execution order"
)
public class DryrunCmd implements Callable<Integer> {

  @Parameters(
      index = "0",
      description = "Path to the pipeline YAML configuration file"
  )
  private File file;

  @Override
  public Integer call() {
    if (!file.exists()) {
      System.err.println(file.getPath() + ":1:1: file not found");
      return 1;
    }

    // parse
    YamlParser parser = new YamlParser(file.getPath());
    Pipeline pipeline = parser.parse();

    List<String> errors = parser.getErrors();

    // validate
    if (pipeline != null) {
      Validator validator = new Validator(file.getPath(), pipeline);
      errors.addAll(validator.validate());
    }

    // if invalid, output errors and exit
    if (!errors.isEmpty()) {
      for (String err : errors) {
        System.err.println(err);
      }
      return 1;
    }

    // compute execution order and print dryrun output
    ExecutionPlanner planner = new ExecutionPlanner(pipeline);
    List<StageExecution> plan = planner.computeOrder();
    System.out.print(formatDryrun(plan));
    return 0;
  }

  /**
   * Formats the execution plan as valid YAML output.
   * Format follows the requirements specification:
   * stage -> job -> image + script.
   */
  static String formatDryrun(List<StageExecution> plan) {
    StringBuilder sb = new StringBuilder();

    for (StageExecution stage : plan) {
      sb.append(stage.getStageName()).append(":\n");

      for (Job job : stage.getJobs()) {
        sb.append("    ").append(job.name).append(":\n");
        sb.append("        image: ").append(job.image).append("\n");
        sb.append("        script:\n");
        for (String cmd : job.script) {
          sb.append("        - ").append(cmd).append("\n");
        }
      }
    }

    return sb.toString();
  }
}

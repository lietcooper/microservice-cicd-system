package cicd.cmd;

import cicd.model.Pipeline;
import cicd.parser.YamlParser;
import cicd.validator.Validator;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

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
    // check git repo
    if (!new File(".git").isDirectory()) {
      System.err.println("error: not in a git repository root directory");
      return 1;
    }

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

    // TODO: compute execution order and print dryrun output (A2 + A3)
    System.out.println("Dryrun: pipeline is valid. Execution order output coming soon.");
    return 0;
  }
}

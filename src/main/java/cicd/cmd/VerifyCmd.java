package cicd.cmd;

import cicd.model.Pipeline;
import cicd.parser.YamlParser;
import cicd.validator.Validator;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command (
    name = "verify",
    description = "Validate a pipeline YAML configuration file"
)
public class VerifyCmd implements Callable<Integer> {

  @Parameters (
      index = "0",
      defaultValue = ".pipelines/default.yaml",
      description = "Path to YAML file (default: .pipelines/default.yaml)"
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

    // output
    if (!errors.isEmpty()) {
      for (String err : errors) {
        System.err.println(err);
      }
      return 1;
    }

    System.out.println("Valid!");
    return 0;
  }
}

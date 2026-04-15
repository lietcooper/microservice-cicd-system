package cicd.cmd;

import cicd.model.Pipeline;
import cicd.parser.YamlParser;
import cicd.validator.Validator;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/** Validates pipeline YAML configuration files. */
@Command(
    name = "verify",
    mixinStandardHelpOptions = true,
    description = "Validate a pipeline YAML configuration file"
)
public class VerifyCmd implements Callable<Integer> {

  @Parameters(
      index = "0",
      defaultValue = ".pipelines/default.yaml",
      description = "Path to YAML file or directory"
  )
  private File file;

  @Override
  public Integer call() {
    if (!file.exists()) {
      System.err.println(file.getPath() + ":1:1: file not found");
      return 1;
    }

    if (file.isDirectory()) {
      return verifyDir(file);
    }

    return verifyFile(file);
  }

  /** Verifies all YAML files in a directory. */
  private int verifyDir(File dir) {
    File[] yamls = dir.listFiles(
        (dd, nn) -> nn.endsWith(".yaml") || nn.endsWith(".yml"));
    if (yamls == null || yamls.length == 0) {
      System.err.println("No YAML files found in " + dir.getPath());
      return 1;
    }

    boolean hasError = false;
    Map<String, String> nameToFile = new HashMap<>();

    for (File ff : yamls) {
      if (verifyFile(ff) != 0) {
        hasError = true;
        continue;
      }

      YamlParser parser = new YamlParser(ff.getPath());
      Pipeline pp = parser.parse();
      if (pp != null && pp.name != null) {
        String pname = pp.name;
        if (nameToFile.containsKey(pname)) {
          System.err.println(ff.getPath() + ": pipeline name `" + pname
              + "` already defined in " + nameToFile.get(pname));
          hasError = true;
        } else {
          nameToFile.put(pname, ff.getPath());
        }
      }
    }

    return hasError ? 1 : 0;
  }

  /** Verifies a single YAML file. */
  private int verifyFile(File ff) {
    YamlParser parser = new YamlParser(ff.getPath());
    Pipeline pp = parser.parse();

    if (!parser.getErrors().isEmpty()) {
      for (String err : parser.getErrors()) {
        System.err.println(err);
      }
      return 1;
    }

    if (pp != null) {
      Validator vv = new Validator(ff.getPath(), pp);
      List<String> errs = vv.validate();
      if (!errs.isEmpty()) {
        for (String err : errs) {
          System.err.println(err);
        }
        return 1;
      }
    }

    System.out.println(ff.getPath() + ": Valid!");
    return 0;
  }
}

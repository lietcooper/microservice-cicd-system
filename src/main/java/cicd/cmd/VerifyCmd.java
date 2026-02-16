package cicd.cmd;


import cicd.parser.YamlParser;
import cicd.model.Pipeline;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import cicd.validator.Validator;
import java.util.List;

@Command(
    name = "verify",
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
    if (!new File(".git").isDirectory()) {
      System.err.println("error: not in a git repository root directory");
      return 1;
    }

    if (!file.exists()) {
      System.err.println(file.getPath() + ":1:1: file not found");
      return 1;
    }

    if (file.isDirectory()) {
      return verifyDir(file);
    }

    return verifyFile(file);
  }

  private int verifyDir(File dir) {
    File[] yamls = dir.listFiles((d, n) -> n.endsWith(".yaml") || n.endsWith(".yml"));
    if (yamls == null || yamls.length == 0) {
      System.err.println("No YAML files found in " + dir.getPath());
      return 1;
    }

    boolean hasError = false;
    Map<String, String> nameToFile = new HashMap<>();

    for (File f : yamls) {
      if (verifyFile(f) != 0) {
        hasError = true;
        continue;
      }

      YamlParser parser = new YamlParser(f.getPath());
      Pipeline p = parser.parse();
      if (p != null && p.name != null) {
        String name = p.name;
        if (nameToFile.containsKey(name)) {
          System.err.println(f.getPath() + ": pipeline name `" + name
              + "` already defined in " + nameToFile.get(name));
          hasError = true;
        } else {
          nameToFile.put(name, f.getPath());
        }
      }
    }

    return hasError ? 1 : 0;
  }

  private int verifyFile(File f) {
    YamlParser parser = new YamlParser(f.getPath());
    Pipeline p = parser.parse();

    if (!parser.getErrors().isEmpty()) {
      for (String err : parser.getErrors()) {
        System.err.println(err);
      }
      return 1;
    }

    if (p != null) {
      Validator v = new Validator(f.getPath(), p);
      List<String> vErrors = v.validate();
      if (!vErrors.isEmpty()) {
        for (String err : vErrors) {
          System.err.println(err);
        }
        return 1;
      }
    }

    System.out.println(f.getPath() + ": Valid!");
    return 0;
  }
}
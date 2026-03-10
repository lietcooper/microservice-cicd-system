package cicd.util;

import cicd.model.Pipeline;
import cicd.parser.YamlParser;
import java.io.File;

/** Finds pipeline definitions by name in the .pipelines directory. */
public class PipelineFinder {

  /** Searches for a pipeline by name in the given base path. */
  public static FindResult findByName(String name, String basePath) {
    File dir = new File(basePath, ".pipelines");
    if (!dir.isDirectory()) {
      return FindResult.err(".pipelines/ directory not found");
    }

    File[] files = dir.listFiles(
        (dd, nn) -> nn.endsWith(".yaml") || nn.endsWith(".yml"));
    if (files == null || files.length == 0) {
      return FindResult.err(
          "no YAML files found in .pipelines/");
    }

    Pipeline result = null;
    String filePath = null;

    for (File ff : files) {
      YamlParser parser = new YamlParser(ff.getPath());
      Pipeline pp = parser.parse();
      if (parser.getErrors().isEmpty() && pp != null
          && name.equals(pp.name)) {
        if (result != null) {
          return FindResult.err(
              "multiple pipelines found with name '"
                  + name + "' in .pipelines/ directory");
        }
        result = pp;
        filePath = ff.getPath();
      }
    }

    if (result != null) {
      return FindResult.ok(result, filePath);
    }

    return FindResult.err(
        "no pipeline found with name '" + name + "'");
  }

  /** Result of a pipeline find operation. */
  public static class FindResult {

    public final Pipeline pipeline;
    public final String filePath;
    public final String error;

    private FindResult(Pipeline pp, String path, String err) {
      this.pipeline = pp;
      this.filePath = path;
      this.error = err;
    }

    static FindResult ok(Pipeline pp, String path) {
      return new FindResult(pp, path, null);
    }

    static FindResult err(String msg) {
      return new FindResult(null, null, msg);
    }

    /** Returns true if the find operation encountered an error. */
    public boolean hasError() {
      return error != null;
    }
  }
}

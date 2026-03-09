package cicd.util;

import java.io.File;

import cicd.model.Pipeline;
import cicd.parser.YamlParser;

public class PipelineFinder {

  public static FindResult findByName(String name, String basePath) {
    File dir = new File(basePath, ".pipelines");
    if (!dir.isDirectory()) {
      return FindResult.err(".pipelines/ directory not found");
    }

    File[] files = dir.listFiles(
        (d, n) -> n.endsWith(".yaml") || n.endsWith(".yml"));
    if (files == null || files.length == 0) {
      return FindResult.err("no YAML files found in .pipelines/");
    }

    Pipeline result = null;
    String filePath = null;

    for (File f : files) {
      YamlParser parser = new YamlParser(f.getPath());
      Pipeline p = parser.parse();
      if (parser.getErrors().isEmpty() && p != null
          && name.equals(p.name)) {
        if (result != null) {
          return FindResult.err("multiple pipelines found with name '" + name + "' in .pipelines/ directory");
        }
        result = p;
        filePath = f.getPath();
      }
    }

    if (result != null) {
      return FindResult.ok(result, filePath);
    }

    return FindResult.err("no pipeline found with name '" + name + "'");
  }

  public static class FindResult {
    public final Pipeline pipeline;
    public final String filePath;
    public final String error;

    private FindResult(Pipeline p, String path, String err) {
      this.pipeline = p;
      this.filePath = path;
      this.error = err;
    }

    static FindResult ok(Pipeline p, String path) {
      return new FindResult(p, path, null);
    }

    static FindResult err(String msg) {
      return new FindResult(null, null, msg);
    }

    public boolean hasError() {
      return error != null;
    }
  }
}

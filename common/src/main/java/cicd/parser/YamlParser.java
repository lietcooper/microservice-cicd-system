package cicd.parser;

import cicd.model.Job;
import cicd.model.Pipeline;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/** Parses pipeline YAML files into Pipeline model objects. */
public class YamlParser {

  private String file;
  private List<String> errors = new ArrayList<>();

  /** Creates a parser for the given file path. */
  public YamlParser(String file) {
    this.file = file;
  }

  /** Returns the list of parsing errors. */
  public List<String> getErrors() {
    return errors;
  }

  /** Parses the YAML file and returns a Pipeline, or null on error. */
  public Pipeline parse() {
    try (FileReader reader = new FileReader(file)) {
      Yaml yaml = new Yaml();
      Node root = yaml.compose(reader);

      if (root == null || !(root instanceof MappingNode)) {
        errors.add(file + ":1:1: empty or invalid YAML file");
        return null;
      }

      return parseRoot((MappingNode) root);

    } catch (FileNotFoundException ex) {
      errors.add(file + ":1:1: file not found");
      return null;
    } catch (Exception ex) {
      errors.add(file + ":1:1: " + ex.getMessage());
      return null;
    }
  }

  private Pipeline parseRoot(MappingNode root) {
    Pipeline pp = new Pipeline();
    Set<String> seenRootKeys = new HashSet<>();

    for (NodeTuple tt : root.getValue()) {
      String key = str(tt.getKeyNode());
      Node val = tt.getValueNode();
      int line = tt.getKeyNode().getStartMark().getLine() + 1;
      int col = tt.getKeyNode().getStartMark().getColumn() + 1;

      if (!seenRootKeys.add(key)) {
        err(tt.getKeyNode(),
            "duplicate key `" + key + "` in YAML root");
      }

      if ("pipeline".equals(key)) {
        parsePipelineBlock(pp, val);
      } else if ("stages".equals(key)) {
        pp.stages = strList(val, "stages");
        pp.stagesExplicitlyDefined = true;
      } else {
        Job job = parseJob(key, val);
        job.line = line;
        job.col = col;
        pp.jobs.put(key, job);
      }
    }
    return pp;
  }

  private void parsePipelineBlock(Pipeline pp, Node node) {
    if (!(node instanceof MappingNode)) {
      err(node, "pipeline must be a mapping");
      return;
    }
    Set<String> seenKeys = new HashSet<>();
    for (NodeTuple tt : ((MappingNode) node).getValue()) {
      String key = str(tt.getKeyNode());
      Node val = tt.getValueNode();

      if (!seenKeys.add(key)) {
        err(tt.getKeyNode(),
            "duplicate key `" + key + "` in pipeline block");
      }

      if ("name".equals(key)) {
        parseName(pp, val);
      } else if ("description".equals(key)) {
        parseDescription(pp, val);
      }
    }
  }

  private void parseName(Pipeline pp, Node val) {
    if (val instanceof ScalarNode) {
      ScalarNode scalar = (ScalarNode) val;
      String tag = scalar.getTag().getValue();
      if (isNonStringTag(tag)) {
        err(val,
            "wrong type of value given for `name` key."
                + " Expected value of type String, given "
                + scalar.getValue());
      } else {
        pp.name = scalar.getValue();
        pp.nameLine = val.getStartMark().getLine() + 1;
        pp.nameCol = val.getStartMark().getColumn() + 1;
      }
    } else {
      err(val,
          "wrong type of value given for `name` key."
              + " Expected value of type String, given "
              + nodeType(val));
    }
  }

  private void parseDescription(Pipeline pp, Node val) {
    if (val instanceof ScalarNode) {
      ScalarNode scalar = (ScalarNode) val;
      String tag = scalar.getTag().getValue();
      if (isNonStringTag(tag)) {
        err(val,
            "wrong type of value given for `description` key."
                + " Expected value of type String, given "
                + scalar.getValue());
      } else {
        pp.desc = scalar.getValue();
      }
    } else {
      err(val,
          "wrong type of value given for `description` key."
              + " Expected value of type String, given "
              + nodeType(val));
    }
  }

  private boolean isNonStringTag(String tag) {
    return tag.contains("int")
        || tag.contains("float")
        || tag.contains("bool");
  }

  private Job parseJob(String name, Node node) {
    Job jj = new Job();
    jj.name = name;

    List<NodeTuple> entries;

    if (node instanceof MappingNode) {
      entries = ((MappingNode) node).getValue();
    } else if (node instanceof SequenceNode) {
      entries = new ArrayList<>();
      for (Node item : ((SequenceNode) node).getValue()) {
        if (item instanceof MappingNode) {
          entries.addAll(((MappingNode) item).getValue());
        }
      }
    } else {
      err(node, "job `" + name + "` must be a mapping");
      return jj;
    }

    Set<String> seenKeys = new HashSet<>();
    for (NodeTuple tt : entries) {
      String key = str(tt.getKeyNode());
      Node val = tt.getValueNode();

      if (!seenKeys.add(key)) {
        err(tt.getKeyNode(),
            "duplicate key `" + key + "` in job `" + name + "`");
      }

      parseJobField(jj, key, val);
    }
    return jj;
  }

  private void parseJobField(Job jj, String key, Node val) {
    if ("stage".equals(key)) {
      parseScalarField(jj, val, "stage");
    } else if ("image".equals(key)) {
      parseScalarField(jj, val, "image");
    } else if ("script".equals(key)) {
      jj.script = strOrList(val, "script");
    } else if ("needs".equals(key)) {
      jj.needs = strList(val, "needs");
      jj.needsExplicitlyDefined = true;
      jj.needsLine = val.getStartMark().getLine() + 1;
      jj.needsCol = val.getStartMark().getColumn() + 1;
    } else if ("failures".equals(key)) {
      parseBooleanField(jj, val);
    }
  }

  private void parseBooleanField(Job jj, Node val) {
    if (val instanceof ScalarNode) {
      ScalarNode scalar = (ScalarNode) val;
      String tag = scalar.getTag().getValue();
      if (tag.contains("bool")) {
        jj.allowFailure = "true".equalsIgnoreCase(scalar.getValue());
      } else {
        err(val,
            "wrong type of value given for `failures` key."
                + " Expected value of type Boolean, given "
                + scalar.getValue());
      }
    } else {
      err(val,
          "wrong type of value given for `failures` key."
              + " Expected value of type Boolean, given "
              + nodeType(val));
    }
  }

  private void parseScalarField(Job jj, Node val, String field) {
    if (val instanceof ScalarNode) {
      ScalarNode scalar = (ScalarNode) val;
      if (isNonStringTag(scalar.getTag().getValue())) {
        err(val,
            "wrong type of value given for `" + field
                + "` key. Expected value of type String, given "
                + scalar.getValue());
      } else if ("stage".equals(field)) {
        jj.stage = scalar.getValue();
      } else if ("image".equals(field)) {
        jj.image = scalar.getValue();
      }
    } else {
      err(val,
          "wrong type of value given for `" + field
              + "` key. Expected value of type String, given "
              + nodeType(val));
    }
  }

  private List<String> strList(Node node, String field) {
    List<String> result = new ArrayList<>();
    if (!(node instanceof SequenceNode)) {
      String actual = (node instanceof ScalarNode)
          ? ((ScalarNode) node).getValue() : nodeType(node);
      err(node,
          "wrong type of value given for `" + field
              + "` key. Expected value of type List, given "
              + actual);
      return result;
    }
    for (Node nn : ((SequenceNode) node).getValue()) {
      if (nn instanceof ScalarNode) {
        ScalarNode scalar = (ScalarNode) nn;
        if (isNonStringTag(scalar.getTag().getValue())) {
          err(nn,
              "wrong type of value in `" + field
                  + "` list. Expected String, given "
                  + scalar.getValue());
        } else {
          result.add(scalar.getValue());
        }
      } else {
        err(nn,
            "wrong type of value in `" + field
                + "` list. Expected String, given "
                + nodeType(nn));
      }
    }
    return result;
  }

  private List<String> strOrList(Node node, String field) {
    if (node instanceof ScalarNode) {
      ScalarNode scalar = (ScalarNode) node;
      if (isNonStringTag(scalar.getTag().getValue())) {
        err(node,
            "wrong type of value given for `" + field
                + "` key. Expected String or List, given "
                + scalar.getValue());
        return new ArrayList<>();
      }
      return List.of(scalar.getValue());
    }
    return strList(node, field);
  }

  private String str(Node node) {
    return node instanceof ScalarNode
        ? ((ScalarNode) node).getValue() : "";
  }

  private String nodeType(Node node) {
    if (node instanceof ScalarNode) {
      return ((ScalarNode) node).getValue();
    }
    if (node instanceof SequenceNode) {
      return "List";
    }
    if (node instanceof MappingNode) {
      return "Mapping";
    }
    return "unknown";
  }

  private void err(Node node, String msg) {
    int line = node.getStartMark().getLine() + 1;
    int col = node.getStartMark().getColumn() + 1;
    errors.add(file + ":" + line + ":" + col + ": " + msg);
  }
}

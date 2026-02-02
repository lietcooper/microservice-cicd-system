package cicd.parser;

import cicd.model.*;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.*;
import java.io.*;
import java.util.*;

public class YamlParser {
    
    private String file;
    private List<String> errors = new ArrayList<>();
    
    public YamlParser(String file) {
        this.file = file;
    }
    
    public List<String> getErrors() {
        return errors;
    }
    
    public Pipeline parse() {
        try (FileReader reader = new FileReader(file)) {
            Yaml yaml = new Yaml();
            Node root = yaml.compose(reader);
            
            if (root == null || !(root instanceof MappingNode)) {
                errors.add(file + ":1:1: empty or invalid YAML file");
                return null;
            }
            
            return parseRoot((MappingNode) root);
            
        } catch (FileNotFoundException e) {
            errors.add(file + ":1:1: file not found");
            return null;
        } catch (Exception e) {
            errors.add(file + ":1:1: " + e.getMessage());
            return null;
        }
    }
    
    private Pipeline parseRoot(MappingNode root) {
        Pipeline p = new Pipeline();
        
        for (NodeTuple t : root.getValue()) {
            String key = str(t.getKeyNode());
            Node val = t.getValueNode();
            int line = t.getKeyNode().getStartMark().getLine() + 1;
            int col = t.getKeyNode().getStartMark().getColumn() + 1;
            
            if ("pipeline".equals(key)) {
                parsePipelineBlock(p, val);
            } else if ("stages".equals(key)) {
                p.stages = strList(val, "stages");
            } else {
                Job job = parseJob(key, val);
                job.line = line;
                job.col = col;
                p.jobs.put(key, job);
            }
        }
        return p;
    }
    
    private void parsePipelineBlock(Pipeline p, Node node) {
        if (!(node instanceof MappingNode)) {
            err(node, "pipeline must be a mapping");
            return;
        }
        for (NodeTuple t : ((MappingNode) node).getValue()) {
            String key = str(t.getKeyNode());
            Node val = t.getValueNode();

          if ("name".equals(key)) {
            if (val instanceof ScalarNode) {
              ScalarNode scalar = (ScalarNode) val;
              // check if it's actually a string, not int/float/bool
              String tag = scalar.getTag().getValue();
              if (tag.equals("tag:yaml.org,2002:int") ||
                  tag.equals("tag:yaml.org,2002:float") ||
                  tag.equals("tag:yaml.org,2002:bool")) {
                err(val, "wrong type of value given for `name` key. Expected value of type String, given " + tagToType(tag));
              } else {
                p.name = scalar.getValue();
                p.nameLine = val.getStartMark().getLine() + 1;
                p.nameCol = val.getStartMark().getColumn() + 1;
              }
            } else {
              err(val, "wrong type of value given for `name` key. Expected value of type String, given " + nodeType(val));
            }
          }
        }
    }
    
    private Job parseJob(String name, Node node) {
        Job j = new Job();
        j.name = name;
        
        if (!(node instanceof MappingNode)) {
            err(node, "job `" + name + "` must be a mapping");
            return j;
        }
        
        for (NodeTuple t : ((MappingNode) node).getValue()) {
            String key = str(t.getKeyNode());
            Node val = t.getValueNode();
            
            if ("stage".equals(key)) {
                if (val instanceof ScalarNode) {
                    j.stage = ((ScalarNode) val).getValue();
                } else {
                    err(val, "wrong type of value given for `stage` key. Expected value of type String, given " + nodeType(val));
                }
            } else if ("image".equals(key)) {
                if (val instanceof ScalarNode) {
                    j.image = ((ScalarNode) val).getValue();
                } else {
                    err(val, "wrong type of value given for `image` key. Expected value of type String, given " + nodeType(val));
                }
            } else if ("script".equals(key)) {
                j.script = strOrList(val, "script");
            } else if ("needs".equals(key)) {
                j.needs = strList(val, "needs");
                j.needsLine = val.getStartMark().getLine() + 1;
                j.needsCol = val.getStartMark().getColumn() + 1;
            }
        }
        return j;
    }
    
    private List<String> strList(Node node, String field) {
        List<String> result = new ArrayList<>();
        if (!(node instanceof SequenceNode)) {
            err(node, "wrong type of value given for `" + field + "` key. Expected value of type List, given " + nodeType(node));
            return result;
        }
        for (Node n : ((SequenceNode) node).getValue()) {
            if (n instanceof ScalarNode) {
                result.add(((ScalarNode) n).getValue());
            }
        }
        return result;
    }
    
    private List<String> strOrList(Node node, String field) {
        if (node instanceof ScalarNode) {
            return List.of(((ScalarNode) node).getValue());
        }
        return strList(node, field);
    }
    
    private String str(Node node) {
        return node instanceof ScalarNode ? ((ScalarNode) node).getValue() : "";
    }
    
    private String nodeType(Node node) {
        if (node instanceof ScalarNode) {
            String val = ((ScalarNode) node).getValue();
            try {
                Integer.parseInt(val);
                return "Integer";
            } catch (Exception e) {
                return "String";
            }
        }
        if (node instanceof SequenceNode) return "List";
        if (node instanceof MappingNode) return "Mapping";
        return "unknown";
    }
    
    private void err(Node node, String msg) {
        int line = node.getStartMark().getLine() + 1;
        int col = node.getStartMark().getColumn() + 1;
        errors.add(file + ":" + line + ":" + col + ": " + msg);
    }

  private String tagToType(String tag) {
    if (tag.contains("int")) return "Integer";
    if (tag.contains("float")) return "Float";
    if (tag.contains("bool")) return "Boolean";
    return "unknown";
  }
}

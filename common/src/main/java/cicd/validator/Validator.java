package cicd.validator;

import cicd.model.Job;
import cicd.model.Pipeline;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates a parsed pipeline for semantic correctness. */
public class Validator {

  private String file;
  private Pipeline pipeline;
  private List<String> errors = new ArrayList<>();

  /** Creates a validator for the given file and pipeline. */
  public Validator(String file, Pipeline pipeline) {
    this.file = file;
    this.pipeline = pipeline;
  }

  /** Validates the pipeline and returns a list of errors. */
  public List<String> validate() {
    if (pipeline == null) {
      return errors;
    }

    if (!pipeline.stagesExplicitlyDefined
        && pipeline.stages.isEmpty()) {
      pipeline.stages = List.of("build", "test", "docs");
    }

    checkPipeline();
    checkStages();
    checkJobs();
    checkNeeds();
    checkCycles();

    return errors;
  }

  private void checkPipeline() {
    if (pipeline.name == null || pipeline.name.isBlank()) {
      errors.add(file + ":1:1: pipeline name is required");
    }
  }

  private void checkStages() {
    if (pipeline.stages.isEmpty()) {
      errors.add(file + ":1:1: at least 1 stage must be defined");
      return;
    }

    Set<String> seen = new HashSet<>();
    for (String ss : pipeline.stages) {
      if (!seen.add(ss)) {
        errors.add(file
            + ":1:1: duplicate stage name `" + ss + "`");
      }
    }

    for (String stage : pipeline.stages) {
      boolean hasJob = false;
      for (Job jj : pipeline.jobs.values()) {
        if (stage.equals(jj.stage)) {
          hasJob = true;
          break;
        }
      }
      if (!hasJob) {
        errors.add(file
            + ":1:1: stage `" + stage + "` has no jobs");
      }
    }
  }

  private void checkJobs() {
    for (Job jj : pipeline.jobs.values()) {
      if (jj.stage == null || jj.stage.isBlank()) {
        errors.add(file + ":" + jj.line + ":" + jj.col
            + ": job `" + jj.name + "` missing stage");
      } else if (!pipeline.stages.contains(jj.stage)) {
        errors.add(file + ":" + jj.line + ":" + jj.col
            + ": job `" + jj.name
            + "` references undefined stage `" + jj.stage + "`");
      }

      if (jj.image == null || jj.image.isBlank()) {
        errors.add(file + ":" + jj.line + ":" + jj.col
            + ": job `" + jj.name + "` missing image");
      }

      if (jj.script == null || jj.script.isEmpty()) {
        errors.add(file + ":" + jj.line + ":" + jj.col
            + ": job `" + jj.name + "` missing script");
      }
    }
  }

  private void checkNeeds() {
    for (Job jj : pipeline.jobs.values()) {
      if (jj.needsExplicitlyDefined && jj.needs.isEmpty()) {
        errors.add(file + ":" + jj.needsLine + ":" + jj.needsCol
            + ": job `" + jj.name + "` has an empty `needs` list."
            + " A needs has a non-empty list of job names.");
      }

      Set<String> seen = new HashSet<>();
      for (String need : jj.needs) {
        if (!seen.add(need)) {
          errors.add(file + ":" + jj.needsLine + ":"
              + jj.needsCol + ": job `" + jj.name
              + "` has duplicate entry `" + need + "` in `needs`");
        }
        if (!pipeline.jobs.containsKey(need)) {
          errors.add(file + ":" + jj.needsLine + ":"
              + jj.needsCol + ": job `" + jj.name + "` needs `"
              + need + "` which does not exist");
        } else {
          Job neededJob = pipeline.jobs.get(need);
          if (jj.stage != null
              && !jj.stage.equals(neededJob.stage)) {
            errors.add(file + ":" + jj.needsLine + ":"
                + jj.needsCol + ": job `" + jj.name + "` needs `"
                + need + "` which is in a different stage `"
                + neededJob.stage
                + "`. Needs must be in the same stage.");
          }
        }
      }
    }
  }

  private void checkCycles() {
    for (String stage : pipeline.stages) {
      Map<String, List<String>> graph = new HashMap<>();
      for (Job jj : pipeline.jobs.values()) {
        if (stage.equals(jj.stage)) {
          graph.put(jj.name, new ArrayList<>(jj.needs));
        }
      }

      for (String start : graph.keySet()) {
        List<String> path = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        if (hasCycle(start, graph, visited, path)) {
          path.add(start);
          Job jj = pipeline.jobs.get(start);
          int line = jj.needsLine > 0 ? jj.needsLine : jj.line;
          int col = jj.needsCol > 0 ? jj.needsCol : jj.col;
          errors.add(file + ":" + line + ":" + col
              + ": cycle detected in `needs` requirements"
              + " for stage `" + stage + "`. "
              + String.join(" -> ", path) + ".");
          return;
        }
      }
    }
  }

  private boolean hasCycle(String node,
      Map<String, List<String>> graph, Set<String> visited,
      List<String> path) {
    if (path.contains(node)) {
      int idx = path.indexOf(node);
      while (idx-- > 0) {
        path.remove(0);
      }
      return true;
    }
    if (visited.contains(node)) {
      return false;
    }

    visited.add(node);
    path.add(node);

    List<String> neighbors =
        graph.getOrDefault(node, List.of());
    for (String next : neighbors) {
      if (hasCycle(next, graph, visited, path)) {
        return true;
      }
    }

    path.remove(path.size() - 1);
    return false;
  }
}

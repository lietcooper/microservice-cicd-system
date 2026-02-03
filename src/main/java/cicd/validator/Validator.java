package cicd.validator;

import cicd.model.Job;
import cicd.model.Pipeline;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Validator {

  private String file;
  private Pipeline p;
  private List<String> errors = new ArrayList<>();

  public Validator(String file, Pipeline p) {
    this.file = file;
    this.p = p;
  }

  public List<String> validate() {
    if (p == null) {
      return errors;
    }

    checkStages();
    checkJobs();
    checkNeeds();
    checkCycles();

    return errors;
  }

  private void checkStages() {
    // rule 1: at least 1 stage
    if (p.stages.isEmpty()) {
      errors.add(file + ":1:1: at least 1 stage must be defined");
      return;
    }

    // rule 2: stage names unique
    Set<String> seen = new HashSet<>();
    for (String s : p.stages) {
      if (seen.contains(s)) {
        errors.add(file + ":1:1: duplicate stage name `" + s + "`");
      }
      seen.add(s);
    }

    // rule 3: no empty stages
    for (String stage : p.stages) {
      boolean hasJob = false;
      for (Job j : p.jobs.values()) {
        if (stage.equals(j.stage)) {
          hasJob = true;
          break;
        }
      }
      if (!hasJob) {
        errors.add(file + ":1:1: stage `" + stage + "` has no jobs");
      }
    }
  }

  private void checkJobs() {
    // rule 4: job names unique
    Set<String> seen = new HashSet<>();
    for (String name : p.jobs.keySet()) {
      if (seen.contains(name)) {
        Job j = p.jobs.get(name);
        errors.add(file + ":" + j.line + ":" + j.col + ": duplicate job name `" + name + "`");
      }
      seen.add(name);
    }

    // check job stage exists
    for (Job j : p.jobs.values()) {
      if (j.stage != null && !p.stages.contains(j.stage)) {
        errors.add(file + ":" + j.line + ":" + j.col + ": job `" + j.name
            + "` references undefined stage `" + j.stage + "`");
      }
    }
  }

  private void checkNeeds() {
    for (Job j : p.jobs.values()) {
      for (String need : j.needs) {
        if (!p.jobs.containsKey(need)) {
          errors.add(file + ":" + j.needsLine + ":" + j.needsCol
              + ": job `" + j.name + "` needs `" + need + "` which does not exist");
        }
      }
    }
  }


  private void checkCycles() {
    // rule 6: no cycles in needs (per stage)
    Map<String, List<String>> graph = new HashMap<>();
    for (Job j : p.jobs.values()) {
      graph.put(j.name, new ArrayList<>(j.needs));
    }

    for (String start : graph.keySet()) {
      List<String> path = new ArrayList<>();
      Set<String> visited = new HashSet<>();
      if (hasCycle(start, graph, visited, path)) {
        path.add(start);
        Job j = p.jobs.get(start);
        int line = j.needsLine > 0 ? j.needsLine : j.line;
        int col = j.needsCol > 0 ? j.needsCol : j.col;
        errors.add(file + ":" + line + ":" + col + ": cycle detected in `needs` requirements. "
            + String.join(" -> ", path) + ".");
        return; // report first cycle only
      }
    }
  }

  private boolean hasCycle(String node, Map<String, List<String>> graph, Set<String> visited,
      List<String> path) {
    if (path.contains(node)) {
      // trim path to start of cycle
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

    List<String> neighbors = graph.getOrDefault(node, List.of());
    for (String next : neighbors) {
      if (hasCycle(next, graph, visited, path)) {
        return true;
      }
    }

    path.remove(path.size() - 1);
    return false;
  }
}

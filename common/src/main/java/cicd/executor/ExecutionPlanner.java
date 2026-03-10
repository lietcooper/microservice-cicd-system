package cicd.executor;

import cicd.model.Job;
import cicd.model.Pipeline;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Computes the execution order for a validated pipeline.
 * Stages are ordered by their position in the stages list.
 * Within each stage, jobs are topologically sorted based on needs dependencies.
 */
public class ExecutionPlanner {

  private final Pipeline pipeline;

  /** Creates a planner for the given pipeline. */
  public ExecutionPlanner(Pipeline pipeline) {
    this.pipeline = pipeline;
  }

  /**
   * Returns the execution plan as an ordered list of stage entries.
   * Each entry is a map with a single key (the stage name) mapped to
   * the list of jobs in execution order for that stage.
   */
  public List<StageExecution> computeOrder() {
    List<StageExecution> plan = new ArrayList<>();

    for (String stage : pipeline.stages) {
      // collect jobs for this stage, preserving insertion order
      LinkedHashMap<String, Job> stageJobs = new LinkedHashMap<>();
      for (Map.Entry<String, Job> entry : pipeline.jobs.entrySet()) {
        if (stage.equals(entry.getValue().stage)) {
          stageJobs.put(entry.getKey(), entry.getValue());
        }
      }

      List<Job> sorted = topologicalSort(stageJobs);
      plan.add(new StageExecution(stage, sorted));
    }

    return plan;
  }

  /**
   * Returns the execution plan with wave-level grouping for parallel execution.
   * Each stage contains a list of waves; jobs within a wave can run in parallel.
   */
  public List<WaveStageExecution> computeWaveOrder() {
    List<WaveStageExecution> plan = new ArrayList<>();

    for (String stage : pipeline.stages) {
      LinkedHashMap<String, Job> stageJobs = new LinkedHashMap<>();
      for (Map.Entry<String, Job> entry : pipeline.jobs.entrySet()) {
        if (stage.equals(entry.getValue().stage)) {
          stageJobs.put(entry.getKey(), entry.getValue());
        }
      }

      List<List<Job>> waves = computeWaves(stageJobs);
      plan.add(new WaveStageExecution(stage, waves));
    }

    return plan;
  }

  /**
   * Groups jobs into waves using a modified Kahn's algorithm.
   * Each iteration collects ALL zero in-degree jobs into one wave,
   * then decrements dependents for the next iteration.
   */
  private List<List<Job>> computeWaves(LinkedHashMap<String, Job> stageJobs) {
    Map<String, Integer> inDegree = new HashMap<>();
    for (String name : stageJobs.keySet()) {
      inDegree.put(name, 0);
    }

    for (Job job : stageJobs.values()) {
      for (String need : job.needs) {
        if (stageJobs.containsKey(need)) {
          inDegree.merge(job.name, 1, Integer::sum);
        }
      }
    }

    List<List<Job>> waves = new ArrayList<>();
    Map<String, Integer> remaining = new HashMap<>(inDegree);

    while (!remaining.isEmpty()) {
      // Collect all jobs with zero in-degree as the current wave
      List<Job> wave = new ArrayList<>();
      List<String> waveNames = new ArrayList<>();
      for (String name : stageJobs.keySet()) {
        if (remaining.containsKey(name) && remaining.get(name) == 0) {
          wave.add(stageJobs.get(name));
          waveNames.add(name);
        }
      }

      if (wave.isEmpty()) {
        throw new IllegalStateException(
            "cycle detected in dependencies for stage jobs: "
                + remaining.keySet());
      }

      // Remove processed jobs and decrement dependents
      for (String name : waveNames) {
        remaining.remove(name);
        for (Job job : stageJobs.values()) {
          if (job.needs.contains(name) && remaining.containsKey(job.name)) {
            remaining.merge(job.name, -1, Integer::sum);
          }
        }
      }

      waves.add(wave);
    }

    return waves;
  }

  /**
   * Kahn's algorithm for topological sort within a stage.
   * Jobs with no unresolved needs are emitted in their original definition order.
   */
  private List<Job> topologicalSort(LinkedHashMap<String, Job> stageJobs) {
    // Build in-degree map (only count needs that are within this stage)
    Map<String, Integer> inDegree = new HashMap<>();
    for (String name : stageJobs.keySet()) {
      inDegree.put(name, 0);
    }

    for (Job job : stageJobs.values()) {
      for (String need : job.needs) {
        if (stageJobs.containsKey(need)) {
          inDegree.merge(job.name, 1, Integer::sum);
        }
      }
    }

    // Seed queue with jobs that have zero in-degree, in definition order
    Queue<String> queue = new LinkedList<>();
    for (String name : stageJobs.keySet()) {
      if (inDegree.get(name) == 0) {
        queue.add(name);
      }
    }

    List<Job> result = new ArrayList<>();
    while (!queue.isEmpty()) {
      String current = queue.poll();
      result.add(stageJobs.get(current));

      // For each job in this stage, if it depends on current, decrement in-degree
      for (Job job : stageJobs.values()) {
        if (job.needs.contains(current) && stageJobs.containsKey(job.name)) {
          int newDeg = inDegree.get(job.name) - 1;
          inDegree.put(job.name, newDeg);
          if (newDeg == 0) {
            queue.add(job.name);
          }
        }
      }
    }

    if (result.size() != stageJobs.size()) {
      throw new IllegalStateException(
          "cycle detected or missing dependency in stage jobs");
    }

    return result;
  }

  /**
   * Represents a single stage and its jobs in execution order.
   */
  public static class StageExecution {

    private final String stageName;
    private final List<Job> jobs;

    /** Creates a stage execution entry. */
    public StageExecution(String stageName, List<Job> jobs) {
      this.stageName = stageName;
      this.jobs = jobs;
    }

    public String getStageName() {
      return stageName;
    }

    public List<Job> getJobs() {
      return jobs;
    }
  }

  /**
   * Represents a stage with its jobs grouped into waves for parallel execution.
   * Jobs within the same wave have no dependencies on each other.
   */
  public static class WaveStageExecution {

    private final String stageName;
    private final List<List<Job>> waves;

    /** Creates a wave stage execution entry. */
    public WaveStageExecution(String stageName, List<List<Job>> waves) {
      this.stageName = stageName;
      this.waves = waves;
    }

    public String getStageName() {
      return stageName;
    }

    public List<List<Job>> getWaves() {
      return waves;
    }
  }
}

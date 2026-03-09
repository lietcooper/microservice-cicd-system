package cicd.executor;

import cicd.model.Job;
import cicd.model.Pipeline;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionPlannerTest {

  @Test
  void testSingleStageNoNeeds() {
    Pipeline pipeline = new Pipeline();
    pipeline.name = "test";
    pipeline.stages = Arrays.asList("build");
    pipeline.jobs = new LinkedHashMap<>();

    Job compile = new Job();
    compile.name = "compile";
    compile.stage = "build";
    compile.image = "gradle:jdk21";
    compile.script = Arrays.asList("gradle build");
    pipeline.jobs.put("compile", compile);

    ExecutionPlanner planner = new ExecutionPlanner(pipeline);
    List<ExecutionPlanner.StageExecution> plan = planner.computeOrder();

    assertEquals(1, plan.size());
    assertEquals("build", plan.get(0).getStageName());
    assertEquals(1, plan.get(0).getJobs().size());
    assertEquals("compile", plan.get(0).getJobs().get(0).name);
  }

  @Test
  void testMultipleStagesSequential() {
    Pipeline pipeline = new Pipeline();
    pipeline.name = "test";
    pipeline.stages = Arrays.asList("build", "test", "deploy");
    pipeline.jobs = new LinkedHashMap<>();

    Job compile = new Job();
    compile.name = "compile";
    compile.stage = "build";
    compile.image = "gradle:jdk21";
    compile.script = Arrays.asList("gradle build");
    pipeline.jobs.put("compile", compile);

    Job unittest = new Job();
    unittest.name = "unittest";
    unittest.stage = "test";
    unittest.image = "gradle:jdk21";
    unittest.script = Arrays.asList("gradle test");
    pipeline.jobs.put("unittest", unittest);

    Job release = new Job();
    release.name = "release";
    release.stage = "deploy";
    release.image = "gradle:jdk21";
    release.script = Arrays.asList("gradle publish");
    pipeline.jobs.put("release", release);

    ExecutionPlanner planner = new ExecutionPlanner(pipeline);
    List<ExecutionPlanner.StageExecution> plan = planner.computeOrder();

    assertEquals(3, plan.size());
    assertEquals("build", plan.get(0).getStageName());
    assertEquals("test", plan.get(1).getStageName());
    assertEquals("deploy", plan.get(2).getStageName());
  }

  @Test
  void testJobsWithNeeds() {
    Pipeline pipeline = new Pipeline();
    pipeline.name = "test";
    pipeline.stages = Arrays.asList("test");
    pipeline.jobs = new LinkedHashMap<>();

    // Job B needs Job A
    Job jobA = new Job();
    jobA.name = "jobA";
    jobA.stage = "test";
    jobA.image = "alpine";
    jobA.script = Arrays.asList("echo A");
    pipeline.jobs.put("jobA", jobA);

    Job jobB = new Job();
    jobB.name = "jobB";
    jobB.stage = "test";
    jobB.image = "alpine";
    jobB.script = Arrays.asList("echo B");
    jobB.needs = Arrays.asList("jobA");
    pipeline.jobs.put("jobB", jobB);

    ExecutionPlanner planner = new ExecutionPlanner(pipeline);
    List<ExecutionPlanner.StageExecution> plan = planner.computeOrder();

    assertEquals(1, plan.size());
    List<Job> jobs = plan.get(0).getJobs();
    assertEquals(2, jobs.size());
    // jobA must come before jobB
    assertEquals("jobA", jobs.get(0).name);
    assertEquals("jobB", jobs.get(1).name);
  }

  @Test
  void testComplexNeedsDiamond() {
    // Diamond dependency: A -> B, A -> C, B -> D, C -> D
    Pipeline pipeline = new Pipeline();
    pipeline.name = "test";
    pipeline.stages = Arrays.asList("build");
    pipeline.jobs = new LinkedHashMap<>();

    Job jobA = new Job();
    jobA.name = "A";
    jobA.stage = "build";
    jobA.image = "alpine";
    jobA.script = Arrays.asList("echo A");
    pipeline.jobs.put("A", jobA);

    Job jobB = new Job();
    jobB.name = "B";
    jobB.stage = "build";
    jobB.image = "alpine";
    jobB.script = Arrays.asList("echo B");
    jobB.needs = Arrays.asList("A");
    pipeline.jobs.put("B", jobB);

    Job jobC = new Job();
    jobC.name = "C";
    jobC.stage = "build";
    jobC.image = "alpine";
    jobC.script = Arrays.asList("echo C");
    jobC.needs = Arrays.asList("A");
    pipeline.jobs.put("C", jobC);

    Job jobD = new Job();
    jobD.name = "D";
    jobD.stage = "build";
    jobD.image = "alpine";
    jobD.script = Arrays.asList("echo D");
    jobD.needs = Arrays.asList("B", "C");
    pipeline.jobs.put("D", jobD);

    ExecutionPlanner planner = new ExecutionPlanner(pipeline);
    List<ExecutionPlanner.StageExecution> plan = planner.computeOrder();

    List<Job> jobs = plan.get(0).getJobs();
    assertEquals(4, jobs.size());

    // A must be first
    assertEquals("A", jobs.get(0).name);
    // D must be last
    assertEquals("D", jobs.get(3).name);
    // B and C can be in any order between A and D
    assertTrue(jobs.get(1).name.equals("B") || jobs.get(1).name.equals("C"));
    assertTrue(jobs.get(2).name.equals("B") || jobs.get(2).name.equals("C"));
  }

  @Test
  void testEmptyPipeline() {
    Pipeline pipeline = new Pipeline();
    pipeline.name = "empty";
    pipeline.stages = Arrays.asList();
    pipeline.jobs = new LinkedHashMap<>();

    ExecutionPlanner planner = new ExecutionPlanner(pipeline);
    List<ExecutionPlanner.StageExecution> plan = planner.computeOrder();

    assertEquals(0, plan.size());
  }

  @Test
  void testMultipleJobsNoNeeds() {
    Pipeline pipeline = new Pipeline();
    pipeline.name = "test";
    pipeline.stages = Arrays.asList("build");
    pipeline.jobs = new LinkedHashMap<>();

    Job job1 = new Job();
    job1.name = "job1";
    job1.stage = "build";
    job1.image = "alpine";
    job1.script = Arrays.asList("echo 1");
    pipeline.jobs.put("job1", job1);

    Job job2 = new Job();
    job2.name = "job2";
    job2.stage = "build";
    job2.image = "alpine";
    job2.script = Arrays.asList("echo 2");
    pipeline.jobs.put("job2", job2);

    Job job3 = new Job();
    job3.name = "job3";
    job3.stage = "build";
    job3.image = "alpine";
    job3.script = Arrays.asList("echo 3");
    pipeline.jobs.put("job3", job3);

    ExecutionPlanner planner = new ExecutionPlanner(pipeline);
    List<ExecutionPlanner.StageExecution> plan = planner.computeOrder();

    assertEquals(1, plan.size());
    assertEquals(3, plan.get(0).getJobs().size());
    // Without needs, jobs should preserve definition order
    assertEquals("job1", plan.get(0).getJobs().get(0).name);
    assertEquals("job2", plan.get(0).getJobs().get(1).name);
    assertEquals("job3", plan.get(0).getJobs().get(2).name);
  }

  // --- Tests for computeWaveOrder() ---

  @Test
  void testWaveOrderSingleWaveNoNeeds() {
    Pipeline pipeline = new Pipeline();
    pipeline.name = "test";
    pipeline.stages = Arrays.asList("build");
    pipeline.jobs = new LinkedHashMap<>();

    Job job1 = new Job();
    job1.name = "job1";
    job1.stage = "build";
    job1.image = "alpine";
    job1.script = Arrays.asList("echo 1");
    pipeline.jobs.put("job1", job1);

    Job job2 = new Job();
    job2.name = "job2";
    job2.stage = "build";
    job2.image = "alpine";
    job2.script = Arrays.asList("echo 2");
    pipeline.jobs.put("job2", job2);

    Job job3 = new Job();
    job3.name = "job3";
    job3.stage = "build";
    job3.image = "alpine";
    job3.script = Arrays.asList("echo 3");
    pipeline.jobs.put("job3", job3);

    ExecutionPlanner planner = new ExecutionPlanner(pipeline);
    List<ExecutionPlanner.WaveStageExecution> plan = planner.computeWaveOrder();

    assertEquals(1, plan.size());
    assertEquals("build", plan.get(0).getStageName());
    // All jobs have no needs, so they should all be in wave 0
    List<List<Job>> waves = plan.get(0).getWaves();
    assertEquals(1, waves.size());
    assertEquals(3, waves.get(0).size());
  }

  @Test
  void testWaveOrderWithNeeds() {
    Pipeline pipeline = new Pipeline();
    pipeline.name = "test";
    pipeline.stages = Arrays.asList("test");
    pipeline.jobs = new LinkedHashMap<>();

    Job jobA = new Job();
    jobA.name = "jobA";
    jobA.stage = "test";
    jobA.image = "alpine";
    jobA.script = Arrays.asList("echo A");
    pipeline.jobs.put("jobA", jobA);

    Job jobB = new Job();
    jobB.name = "jobB";
    jobB.stage = "test";
    jobB.image = "alpine";
    jobB.script = Arrays.asList("echo B");
    jobB.needs = Arrays.asList("jobA");
    pipeline.jobs.put("jobB", jobB);

    ExecutionPlanner planner = new ExecutionPlanner(pipeline);
    List<ExecutionPlanner.WaveStageExecution> plan = planner.computeWaveOrder();

    List<List<Job>> waves = plan.get(0).getWaves();
    assertEquals(2, waves.size());
    // Wave 0: jobA (no needs)
    assertEquals(1, waves.get(0).size());
    assertEquals("jobA", waves.get(0).get(0).name);
    // Wave 1: jobB (needs jobA)
    assertEquals(1, waves.get(1).size());
    assertEquals("jobB", waves.get(1).get(0).name);
  }

  @Test
  void testWaveOrderDiamondDependency() {
    // A -> B, A -> C, B -> D, C -> D
    // Wave 0: [A], Wave 1: [B, C], Wave 2: [D]
    Pipeline pipeline = new Pipeline();
    pipeline.name = "test";
    pipeline.stages = Arrays.asList("build");
    pipeline.jobs = new LinkedHashMap<>();

    Job jobA = new Job();
    jobA.name = "A";
    jobA.stage = "build";
    jobA.image = "alpine";
    jobA.script = Arrays.asList("echo A");
    pipeline.jobs.put("A", jobA);

    Job jobB = new Job();
    jobB.name = "B";
    jobB.stage = "build";
    jobB.image = "alpine";
    jobB.script = Arrays.asList("echo B");
    jobB.needs = Arrays.asList("A");
    pipeline.jobs.put("B", jobB);

    Job jobC = new Job();
    jobC.name = "C";
    jobC.stage = "build";
    jobC.image = "alpine";
    jobC.script = Arrays.asList("echo C");
    jobC.needs = Arrays.asList("A");
    pipeline.jobs.put("C", jobC);

    Job jobD = new Job();
    jobD.name = "D";
    jobD.stage = "build";
    jobD.image = "alpine";
    jobD.script = Arrays.asList("echo D");
    jobD.needs = Arrays.asList("B", "C");
    pipeline.jobs.put("D", jobD);

    ExecutionPlanner planner = new ExecutionPlanner(pipeline);
    List<ExecutionPlanner.WaveStageExecution> plan = planner.computeWaveOrder();

    List<List<Job>> waves = plan.get(0).getWaves();
    assertEquals(3, waves.size());

    // Wave 0: A
    assertEquals(1, waves.get(0).size());
    assertEquals("A", waves.get(0).get(0).name);

    // Wave 1: B and C (parallel)
    assertEquals(2, waves.get(1).size());
    List<String> wave1Names = Arrays.asList(
        waves.get(1).get(0).name, waves.get(1).get(1).name);
    assertTrue(wave1Names.contains("B"));
    assertTrue(wave1Names.contains("C"));

    // Wave 2: D
    assertEquals(1, waves.get(2).size());
    assertEquals("D", waves.get(2).get(0).name);
  }

  @Test
  void testWaveOrderEmptyPipeline() {
    Pipeline pipeline = new Pipeline();
    pipeline.name = "empty";
    pipeline.stages = Arrays.asList();
    pipeline.jobs = new LinkedHashMap<>();

    ExecutionPlanner planner = new ExecutionPlanner(pipeline);
    List<ExecutionPlanner.WaveStageExecution> plan = planner.computeWaveOrder();

    assertEquals(0, plan.size());
  }

  @Test
  void testWaveOrderMultipleStages() {
    Pipeline pipeline = new Pipeline();
    pipeline.name = "test";
    pipeline.stages = Arrays.asList("build", "test");
    pipeline.jobs = new LinkedHashMap<>();

    Job compile = new Job();
    compile.name = "compile";
    compile.stage = "build";
    compile.image = "gradle:jdk21";
    compile.script = Arrays.asList("gradle build");
    pipeline.jobs.put("compile", compile);

    Job lint = new Job();
    lint.name = "lint";
    lint.stage = "test";
    lint.image = "alpine";
    lint.script = Arrays.asList("lint");
    pipeline.jobs.put("lint", lint);

    Job unittest = new Job();
    unittest.name = "unittest";
    unittest.stage = "test";
    unittest.image = "alpine";
    unittest.script = Arrays.asList("test");
    unittest.needs = Arrays.asList("lint");
    pipeline.jobs.put("unittest", unittest);

    ExecutionPlanner planner = new ExecutionPlanner(pipeline);
    List<ExecutionPlanner.WaveStageExecution> plan = planner.computeWaveOrder();

    assertEquals(2, plan.size());

    // Build stage: 1 wave with 1 job
    assertEquals("build", plan.get(0).getStageName());
    assertEquals(1, plan.get(0).getWaves().size());
    assertEquals(1, plan.get(0).getWaves().get(0).size());

    // Test stage: 2 waves (lint then unittest)
    assertEquals("test", plan.get(1).getStageName());
    assertEquals(2, plan.get(1).getWaves().size());
    assertEquals("lint", plan.get(1).getWaves().get(0).get(0).name);
    assertEquals("unittest", plan.get(1).getWaves().get(1).get(0).name);
  }

  @Test
  void testWaveOrderChainedDependencies() {
    // job1 -> job2 -> job3 -> job4
    // Each should be in its own wave
    Pipeline pipeline = new Pipeline();
    pipeline.name = "test";
    pipeline.stages = Arrays.asList("build");
    pipeline.jobs = new LinkedHashMap<>();

    Job job1 = new Job();
    job1.name = "job1";
    job1.stage = "build";
    job1.image = "alpine";
    job1.script = Arrays.asList("echo 1");
    pipeline.jobs.put("job1", job1);

    Job job2 = new Job();
    job2.name = "job2";
    job2.stage = "build";
    job2.image = "alpine";
    job2.script = Arrays.asList("echo 2");
    job2.needs = Arrays.asList("job1");
    pipeline.jobs.put("job2", job2);

    Job job3 = new Job();
    job3.name = "job3";
    job3.stage = "build";
    job3.image = "alpine";
    job3.script = Arrays.asList("echo 3");
    job3.needs = Arrays.asList("job2");
    pipeline.jobs.put("job3", job3);

    Job job4 = new Job();
    job4.name = "job4";
    job4.stage = "build";
    job4.image = "alpine";
    job4.script = Arrays.asList("echo 4");
    job4.needs = Arrays.asList("job3");
    pipeline.jobs.put("job4", job4);

    ExecutionPlanner planner = new ExecutionPlanner(pipeline);
    List<ExecutionPlanner.WaveStageExecution> plan = planner.computeWaveOrder();

    List<List<Job>> waves = plan.get(0).getWaves();
    assertEquals(4, waves.size());
    assertEquals("job1", waves.get(0).get(0).name);
    assertEquals("job2", waves.get(1).get(0).name);
    assertEquals("job3", waves.get(2).get(0).name);
    assertEquals("job4", waves.get(3).get(0).name);
  }
}

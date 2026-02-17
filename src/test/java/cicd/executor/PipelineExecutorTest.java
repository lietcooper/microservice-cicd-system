package cicd.executor;

import cicd.docker.DockerRunner;
import cicd.model.Job;
import cicd.model.Pipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PipelineExecutorTest {

  private ByteArrayOutputStream outContent;
  private ByteArrayOutputStream errContent;
  private PrintStream originalOut;
  private PrintStream originalErr;

  @BeforeEach
  void setUp() {
    outContent = new ByteArrayOutputStream();
    errContent = new ByteArrayOutputStream();
    originalOut = System.out;
    originalErr = System.err;
    System.setOut(new PrintStream(outContent));
    System.setErr(new PrintStream(errContent));
  }

  void tearDown() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  private Pipeline createSimplePipeline() {
    Pipeline pipeline = new Pipeline();
    pipeline.name = "test-pipeline";
    pipeline.stages = Arrays.asList("build");
    pipeline.jobs = new LinkedHashMap<>();

    Job compile = new Job();
    compile.name = "compile";
    compile.stage = "build";
    compile.image = "gradle:jdk21";
    compile.script = Arrays.asList("gradle build");
    pipeline.jobs.put("compile", compile);

    return pipeline;
  }

  private Pipeline createMultiStagePipeline() {
    Pipeline pipeline = new Pipeline();
    pipeline.name = "multi-stage";
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

    Job deploy = new Job();
    deploy.name = "deploy";
    deploy.stage = "deploy";
    deploy.image = "alpine";
    deploy.script = Arrays.asList("echo deploying");
    pipeline.jobs.put("deploy", deploy);

    return pipeline;
  }

  // Mock DockerRunner that always succeeds
  static class AlwaysSuccessRunner implements DockerRunner {
    @Override
    public JobResult runJob(String image, List<String> script, String repoPath) {
      return new JobResult("job", 0, "Success output");
    }
  }

  // Mock DockerRunner that always fails
  static class AlwaysFailRunner implements DockerRunner {
    @Override
    public JobResult runJob(String image, List<String> script, String repoPath) {
      return new JobResult("job", 1, "Error output");
    }
  }

  // Mock DockerRunner that fails on specific job
  static class FailOnJobRunner implements DockerRunner {
    private final String failJobName;
    private String lastImage;

    FailOnJobRunner(String failJobName) {
      this.failJobName = failJobName;
    }

    @Override
    public JobResult runJob(String image, List<String> script, String repoPath) {
      lastImage = image;
      // We identify jobs by their image or script content
      if (script.contains("gradle test") && failJobName.equals("unittest")) {
        return new JobResult(failJobName, 1, "Test failed");
      }
      return new JobResult("job", 0, "Success");
    }
  }

  // Mock DockerRunner that tracks execution order
  static class TrackingRunner implements DockerRunner {
    private final java.util.List<String> executedImages = new java.util.ArrayList<>();

    @Override
    public JobResult runJob(String image, List<String> script, String repoPath) {
      executedImages.add(image);
      return new JobResult("job", 0, "ok");
    }

    public List<String> getExecutedImages() {
      return executedImages;
    }
  }

  @Test
  void testAllJobsPass() {
    Pipeline pipeline = createSimplePipeline();
    PipelineExecutor executor = new PipelineExecutor(new AlwaysSuccessRunner(), "/tmp/repo");

    boolean result = executor.execute(pipeline);

    assertTrue(result);
    tearDown();
    String output = outContent.toString();
    assertTrue(output.contains("Pipeline PASSED"));
    assertTrue(output.contains("Stage: build"));
    assertTrue(output.contains("Job: compile"));
  }

  @Test
  void testFirstJobFails() {
    Pipeline pipeline = createSimplePipeline();
    PipelineExecutor executor = new PipelineExecutor(new AlwaysFailRunner(), "/tmp/repo");

    boolean result = executor.execute(pipeline);

    assertFalse(result);
    tearDown();
    String errOutput = errContent.toString();
    assertTrue(errOutput.contains("Pipeline FAILED"));
    assertTrue(errOutput.contains("failed"));
  }

  @Test
  void testStopOnFirstFailure() {
    Pipeline pipeline = createMultiStagePipeline();
    TrackingRunner tracker = new TrackingRunner() {
      private int callCount = 0;

      @Override
      public JobResult runJob(String image, List<String> script, String repoPath) {
        callCount++;
        if (callCount == 2) { // Fail on second job (unittest)
          return new JobResult("unittest", 1, "Test failed");
        }
        return new JobResult("job", 0, "ok");
      }
    };

    PipelineExecutor executor = new PipelineExecutor(tracker, "/tmp/repo");
    boolean result = executor.execute(pipeline);

    assertFalse(result);
    // Pipeline should stop, deploy job should NOT run
    tearDown();
    String output = outContent.toString();
    assertFalse(output.contains("Stage: deploy"));
  }

  @Test
  void testMultiStageExecution() {
    Pipeline pipeline = createMultiStagePipeline();
    TrackingRunner tracker = new TrackingRunner();

    PipelineExecutor executor = new PipelineExecutor(tracker, "/tmp/repo");
    boolean result = executor.execute(pipeline);

    assertTrue(result);
    // All 3 jobs should have been executed
    assertEquals(3, tracker.getExecutedImages().size());
    tearDown();
    String output = outContent.toString();
    assertTrue(output.contains("Stage: build"));
    assertTrue(output.contains("Stage: test"));
    assertTrue(output.contains("Stage: deploy"));
  }

  @Test
  void testPipelineNamePrinted() {
    Pipeline pipeline = createSimplePipeline();
    pipeline.name = "my-custom-pipeline";

    PipelineExecutor executor = new PipelineExecutor(new AlwaysSuccessRunner(), "/tmp/repo");
    executor.execute(pipeline);

    tearDown();
    String output = outContent.toString();
    assertTrue(output.contains("my-custom-pipeline"));
  }

  @Test
  void testJobOutputPrinted() {
    DockerRunner runner = (image, script, repoPath) ->
        new JobResult("job", 0, "Line 1\nLine 2\nLine 3");

    Pipeline pipeline = createSimplePipeline();
    PipelineExecutor executor = new PipelineExecutor(runner, "/tmp/repo");
    executor.execute(pipeline);

    tearDown();
    String output = outContent.toString();
    assertTrue(output.contains("Line 1"));
    assertTrue(output.contains("Line 2"));
    assertTrue(output.contains("Line 3"));
  }

  @Test
  void testEmptyOutputHandled() {
    DockerRunner runner = (image, script, repoPath) ->
        new JobResult("job", 0, "");

    Pipeline pipeline = createSimplePipeline();
    PipelineExecutor executor = new PipelineExecutor(runner, "/tmp/repo");

    boolean result = executor.execute(pipeline);

    assertTrue(result);
    tearDown();
    String output = outContent.toString();
    assertTrue(output.contains("passed"));
  }

  @Test
  void testNullOutputHandled() {
    DockerRunner runner = (image, script, repoPath) ->
        new JobResult("job", 0, null);

    Pipeline pipeline = createSimplePipeline();
    PipelineExecutor executor = new PipelineExecutor(runner, "/tmp/repo");

    boolean result = executor.execute(pipeline);

    assertTrue(result);
  }

  @Test
  void testJobsWithNeeds() {
    Pipeline pipeline = new Pipeline();
    pipeline.name = "needs-test";
    pipeline.stages = Arrays.asList("build");
    pipeline.jobs = new LinkedHashMap<>();

    // Add jobB first (which needs jobA)
    Job jobB = new Job();
    jobB.name = "jobB";
    jobB.stage = "build";
    jobB.image = "alpine:B";
    jobB.script = Arrays.asList("echo B");
    jobB.needs = Arrays.asList("jobA");
    pipeline.jobs.put("jobB", jobB);

    // Add jobA second
    Job jobA = new Job();
    jobA.name = "jobA";
    jobA.stage = "build";
    jobA.image = "alpine:A";
    jobA.script = Arrays.asList("echo A");
    pipeline.jobs.put("jobA", jobA);

    TrackingRunner tracker = new TrackingRunner();
    PipelineExecutor executor = new PipelineExecutor(tracker, "/tmp/repo");
    executor.execute(pipeline);

    List<String> order = tracker.getExecutedImages();
    assertEquals(2, order.size());
    // jobA must run before jobB despite definition order
    assertEquals("alpine:A", order.get(0));
    assertEquals("alpine:B", order.get(1));
  }
}

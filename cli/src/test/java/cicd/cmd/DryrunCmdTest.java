package cicd.cmd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cicd.executor.ExecutionPlanner;
import cicd.executor.ExecutionPlanner.StageExecution;
import cicd.model.Job;
import cicd.model.Pipeline;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** Tests for DryrunCmd CLI invocation and formatDryrun static method. */
class DryrunCmdTest {

  @TempDir
  Path tmp;

  // ── formatDryrun (static, unit testable) ────────────────────────────────────

  @Test
  void singleStageAndJob() {
    Pipeline p = new Pipeline();
    p.name = "test";
    p.stages = List.of("build");

    Job compile = new Job();
    compile.name = "compile";
    compile.stage = "build";
    compile.image = "gradle:jdk17";
    compile.script = List.of("./gradlew build");
    p.jobs.put("compile", compile);

    ExecutionPlanner planner = new ExecutionPlanner(p);
    List<StageExecution> plan = planner.computeOrder();
    String output = DryrunCmd.formatDryrun(plan);

    String expected = """
        build:
            compile:
                image: gradle:jdk17
                script:
                - ./gradlew build
                failures: false
        """;

    assertEquals(expected.stripIndent(), output);
  }

  @Test
  void multipleStagesMultipleJobs() {
    Pipeline p = new Pipeline();
    p.name = "multi";
    p.stages = List.of("build", "test", "docs");

    Job compile = new Job();
    compile.name = "compile";
    compile.stage = "build";
    compile.image = "gradle:jdk17";
    compile.script = List.of("./gradlew classes");
    p.jobs.put("compile", compile);

    Job unittest = new Job();
    unittest.name = "unittest";
    unittest.stage = "test";
    unittest.image = "gradle:jdk17";
    unittest.script = List.of("./gradlew test");
    p.jobs.put("unittest", unittest);

    Job reports = new Job();
    reports.name = "reports";
    reports.stage = "test";
    reports.image = "gradle:jdk17";
    reports.script = List.of("./gradlew check");
    p.jobs.put("reports", reports);

    Job javadoc = new Job();
    javadoc.name = "javadoc";
    javadoc.stage = "docs";
    javadoc.image = "gradle:jdk17";
    javadoc.script = List.of("./gradlew javadoc");
    p.jobs.put("javadoc", javadoc);

    ExecutionPlanner planner = new ExecutionPlanner(p);
    List<StageExecution> plan = planner.computeOrder();
    String output = DryrunCmd.formatDryrun(plan);

    String expected = """
        build:
            compile:
                image: gradle:jdk17
                script:
                - ./gradlew classes
                failures: false
        test:
            unittest:
                image: gradle:jdk17
                script:
                - ./gradlew test
                failures: false
            reports:
                image: gradle:jdk17
                script:
                - ./gradlew check
                failures: false
        docs:
            javadoc:
                image: gradle:jdk17
                script:
                - ./gradlew javadoc
                failures: false
        """;

    assertEquals(expected.stripIndent(), output);
  }

  @Test
  void jobWithMultipleScripts() {
    Pipeline p = new Pipeline();
    p.name = "scripts";
    p.stages = List.of("test");

    Job statics = new Job();
    statics.name = "statics";
    statics.stage = "test";
    statics.image = "gradle:jdk21";
    statics.script = List.of("gradle checkstyleMain", "gradle checkstyleTest",
        "gradle spotbugsMain");
    p.jobs.put("statics", statics);

    ExecutionPlanner planner = new ExecutionPlanner(p);
    List<StageExecution> plan = planner.computeOrder();
    String output = DryrunCmd.formatDryrun(plan);

    String expected = """
        test:
            statics:
                image: gradle:jdk21
                script:
                - gradle checkstyleMain
                - gradle checkstyleTest
                - gradle spotbugsMain
                failures: false
        """;

    assertEquals(expected.stripIndent(), output);
  }

  @Test
  void needsDependencyOrdering() {
    Pipeline p = new Pipeline();
    p.name = "deps";
    p.stages = List.of("test");

    // Define job2 first, but it needs job1
    Job job2 = new Job();
    job2.name = "job2";
    job2.stage = "test";
    job2.image = "alpine";
    job2.script = List.of("echo 2");
    job2.needs = List.of("job1");
    p.jobs.put("job2", job2);

    Job job1 = new Job();
    job1.name = "job1";
    job1.stage = "test";
    job1.image = "alpine";
    job1.script = List.of("echo 1");
    p.jobs.put("job1", job1);

    ExecutionPlanner planner = new ExecutionPlanner(p);
    List<StageExecution> plan = planner.computeOrder();
    String output = DryrunCmd.formatDryrun(plan);

    // job1 must come before job2 because of needs dependency
    String expected = """
        test:
            job1:
                image: alpine
                script:
                - echo 1
                failures: false
            job2:
                image: alpine
                script:
                - echo 2
                failures: false
        """;

    assertEquals(expected.stripIndent(), output);
  }

  @Test
  void chainedNeedsDependency() {
    Pipeline p = new Pipeline();
    p.name = "chain";
    p.stages = List.of("build");

    // Define in reverse order: job3 -> job2 -> job1
    Job job3 = new Job();
    job3.name = "job3";
    job3.stage = "build";
    job3.image = "alpine";
    job3.script = List.of("echo 3");
    job3.needs = List.of("job2");
    p.jobs.put("job3", job3);

    Job job2 = new Job();
    job2.name = "job2";
    job2.stage = "build";
    job2.image = "alpine";
    job2.script = List.of("echo 2");
    job2.needs = List.of("job1");
    p.jobs.put("job2", job2);

    Job job1 = new Job();
    job1.name = "job1";
    job1.stage = "build";
    job1.image = "alpine";
    job1.script = List.of("echo 1");
    p.jobs.put("job1", job1);

    ExecutionPlanner planner = new ExecutionPlanner(p);
    List<StageExecution> plan = planner.computeOrder();
    String output = DryrunCmd.formatDryrun(plan);

    // Topological order: job1 -> job2 -> job3
    String expected = """
        build:
            job1:
                image: alpine
                script:
                - echo 1
                failures: false
            job2:
                image: alpine
                script:
                - echo 2
                failures: false
            job3:
                image: alpine
                script:
                - echo 3
                failures: false
        """;

    assertEquals(expected.stripIndent(), output);
  }

  @Test
  void emptyPlanProducesEmptyOutput() {
    Pipeline p = new Pipeline();
    p.name = "empty";
    p.stages = new ArrayList<>();

    ExecutionPlanner planner = new ExecutionPlanner(p);
    List<StageExecution> plan = planner.computeOrder();
    String output = DryrunCmd.formatDryrun(plan);

    assertEquals("", output);
  }

  // ── CLI invocation tests ─────────────────────────────────────────────────────

  @Test
  void dryrunWithValidFile() throws IOException {
    String yaml = """
        pipeline:
          name: dryruntest
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo build
        """;
    Path f = tmp.resolve("test.yaml");
    Files.writeString(f, yaml);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    System.setOut(new PrintStream(out));
    System.setErr(new PrintStream(err));

    int code = new CommandLine(new DryrunCmd()).execute(f.toString());

    System.setOut(System.out);
    System.setErr(System.err);

    assertEquals(0, code);
    String output = out.toString();
    assertTrue(output.contains("build:"));
    assertTrue(output.contains("compile:"));
    assertTrue(output.contains("alpine"));
  }

  @Test
  void dryrunWithInvalidFileReturnsExitCode1() throws IOException {
    String yaml = """
        pipeline:
          name: bad
        stages: []
        """;
    Path f = tmp.resolve("invalid.yaml");
    Files.writeString(f, yaml);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    System.setOut(new PrintStream(out));
    System.setErr(new PrintStream(err));

    int code = new CommandLine(new DryrunCmd()).execute(f.toString());

    System.setOut(System.out);
    System.setErr(System.err);

    assertEquals(1, code);
  }

  @Test
  void dryrunWithNonExistentFileReturnsExitCode1() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    System.setOut(new PrintStream(out));
    System.setErr(new PrintStream(err));

    int code = new CommandLine(new DryrunCmd()).execute(
        tmp.resolve("nonexistent.yaml").toString());

    System.setOut(System.out);
    System.setErr(System.err);

    assertEquals(1, code);
    assertTrue(err.toString().contains("file not found"));
  }

  @Test
  void dryrunOutputContainsImageAndScript() throws IOException {
    String yaml = """
        pipeline:
          name: imgtest
        stages:
          - test
        mytest:
          stage: test
          image: gradle:jdk21
          script:
            - ./gradlew test
            - ./gradlew jacocoReport
        """;
    Path f = tmp.resolve("imgtest.yaml");
    Files.writeString(f, yaml);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    System.setOut(new PrintStream(out));

    int code = new CommandLine(new DryrunCmd()).execute(f.toString());

    System.setOut(System.out);

    assertEquals(0, code);
    String output = out.toString();
    assertTrue(output.contains("image: gradle:jdk21"));
    assertTrue(output.contains("./gradlew test"));
    assertTrue(output.contains("./gradlew jacocoReport"));
  }

  // ── Additional edge cases ─────────────────────────────────────────────────

  @Test
  void dryrunWithCycleReturnsExitCode1() throws IOException {
    String yaml = """
        pipeline:
          name: cycledry
        stages:
          - build
        jobA:
          stage: build
          image: alpine
          script: echo A
          needs:
            - jobB
        jobB:
          stage: build
          image: alpine
          script: echo B
          needs:
            - jobA
        """;
    Path f = tmp.resolve("cycledry.yaml");
    Files.writeString(f, yaml);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    System.setOut(new PrintStream(out));
    System.setErr(new PrintStream(err));

    int code = new CommandLine(new DryrunCmd()).execute(f.toString());

    System.setOut(System.out);
    System.setErr(System.err);

    assertEquals(1, code);
    assertTrue(err.toString().contains("cycle detected"));
  }

  @Test
  void dryrunWithEmptyStageReturnsExitCode1() throws IOException {
    String yaml = """
        pipeline:
          name: emptystagedry
        stages:
          - build
          - test
        compile:
          stage: build
          image: alpine
          script: echo compile
        """;
    Path f = tmp.resolve("emptystagedry.yaml");
    Files.writeString(f, yaml);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    System.setOut(new PrintStream(out));
    System.setErr(new PrintStream(err));

    int code = new CommandLine(new DryrunCmd()).execute(f.toString());

    System.setOut(System.out);
    System.setErr(System.err);

    assertEquals(1, code);
  }

  @Test
  void dryrunOutputShowsNeedsOrderedBeforeDependent() throws IOException {
    String yaml = """
        pipeline:
          name: ordercheck
        stages:
          - build
        job2:
          stage: build
          image: alpine
          script: echo 2
          needs:
            - job1
        job1:
          stage: build
          image: alpine
          script: echo 1
        """;
    Path f = tmp.resolve("ordercheck.yaml");
    Files.writeString(f, yaml);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    System.setOut(new PrintStream(out));

    int code = new CommandLine(new DryrunCmd()).execute(f.toString());

    System.setOut(System.out);

    assertEquals(0, code);
    String output = out.toString();

    // job1 must appear before job2 in the output
    int job1Pos = output.indexOf("job1:");
    int job2Pos = output.indexOf("job2:");
    assertTrue(job1Pos < job2Pos, "job1 must appear before job2");
  }

  @Test
  void dryrunErrorReportContainsFilePath() throws IOException {
    Path f = tmp.resolve("badfile.yaml");
    Files.writeString(f, "pipeline:\n  name: 123\n");

    ByteArrayOutputStream err = new ByteArrayOutputStream();
    System.setErr(new PrintStream(err));

    int code = new CommandLine(new DryrunCmd()).execute(f.toString());

    System.setErr(System.err);

    assertEquals(1, code);
    // Error output should contain the file path
    assertTrue(err.toString().contains("badfile.yaml"));
  }

  @Test
  void dryrunWithMissingFileReportsFileNameInError() {
    String nonexistentPath = tmp.resolve("does-not-exist.yaml").toString();

    ByteArrayOutputStream err = new ByteArrayOutputStream();
    System.setErr(new PrintStream(err));

    int code = new CommandLine(new DryrunCmd()).execute(nonexistentPath);

    System.setErr(System.err);

    assertEquals(1, code);
    assertTrue(err.toString().contains("file not found"));
  }

  @Test
  void formatDryrunWithNoStagesProducesEmptyString() {
    Pipeline pp = new Pipeline();
    pp.name = "nonstages";
    pp.stages = new ArrayList<>();

    ExecutionPlanner planner = new ExecutionPlanner(pp);
    List<StageExecution> plan = planner.computeOrder();

    assertEquals("", DryrunCmd.formatDryrun(plan));
  }

  @Test
  void formatDryrunIndentationIsCorrect() {
    Pipeline pp = new Pipeline();
    pp.name = "indenttest";
    pp.stages = List.of("build");

    Job jj = new Job();
    jj.name = "compile";
    jj.stage = "build";
    jj.image = "alpine:latest";
    jj.script = List.of("echo hello");
    pp.jobs.put("compile", jj);

    ExecutionPlanner planner = new ExecutionPlanner(pp);
    List<StageExecution> plan = planner.computeOrder();
    String output = DryrunCmd.formatDryrun(plan);

    // Job should be indented under stage (4 spaces)
    assertTrue(output.contains("    compile:"), "Job should have 4-space indent");
    // Image should be indented under job (8 spaces)
    assertTrue(output.contains("        image: alpine:latest"),
        "Image should have 8-space indent");
    // Script items should have proper indent
    assertTrue(output.contains("        - echo hello"),
        "Script items should have 8-space indent");
  }
}

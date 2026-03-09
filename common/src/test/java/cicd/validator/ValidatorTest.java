package cicd.validator;

import cicd.model.Job;
import cicd.model.Pipeline;
import cicd.parser.YamlParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidatorTest {

  @TempDir
  Path tmp;

  // ── Happy paths ──────────────────────────────────────────────────────────────

  @Test
  void validPipeline() throws IOException {
    String yaml = """
        pipeline:
          name: test
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo hi
        """;

    List<String> errors = validate(yaml);
    assertTrue(errors.isEmpty());
  }

  @Test
  void validPipelineMultipleStagesAndJobs() throws IOException {
    String yaml = """
        pipeline:
          name: multi
        stages:
          - build
          - test
        compile:
          stage: build
          image: alpine
          script: echo build
        unittest:
          stage: test
          image: alpine
          script: echo test
        """;

    List<String> errors = validate(yaml);
    assertTrue(errors.isEmpty());
  }

  @Test
  void validNeedsDependency() throws IOException {
    String yaml = """
        pipeline:
          name: test
        stages:
          - build
        jobA:
          stage: build
          image: alpine
          script: echo A
        jobB:
          stage: build
          image: alpine
          script: echo B
          needs:
            - jobA
        """;

    List<String> errors = validate(yaml);
    assertTrue(errors.isEmpty());
  }

  @Test
  void validNullPipelineReturnsEmptyErrors() {
    // When pipeline is null (parse failed), validate returns no errors
    Validator v = new Validator("file.yaml", null);
    List<String> errors = v.validate();
    assertTrue(errors.isEmpty());
  }

  // ── Stage rules ──────────────────────────────────────────────────────────────

  @Test
  void noStages() throws IOException {
    String yaml = """
        pipeline:
          name: test
        stages: []
        """;

    List<String> errors = validate(yaml);
    assertTrue(errors.stream().anyMatch(e -> e.contains("at least 1 stage")));
  }

  @Test
  void emptyStage() throws IOException {
    String yaml = """
        pipeline:
          name: test
        stages:
          - build
          - test
        compile:
          stage: build
          image: alpine
          script: echo
        """;

    List<String> errors = validate(yaml);
    assertTrue(errors.stream().anyMatch(e -> e.contains("stage `test` has no jobs")));
  }

  @Test
  void duplicateStageName() throws IOException {
    // Build a pipeline programmatically to test duplicate stage detection
    Pipeline p = new Pipeline();
    p.name = "test";
    p.stages = new ArrayList<>(List.of("build", "build", "test"));
    Job j1 = new Job();
    j1.name = "compile";
    j1.stage = "build";
    p.jobs.put("compile", j1);
    Job j2 = new Job();
    j2.name = "unittest";
    j2.stage = "test";
    p.jobs.put("unittest", j2);

    Validator v = new Validator("file.yaml", p);
    List<String> errors = v.validate();
    assertTrue(errors.stream().anyMatch(e -> e.contains("duplicate stage name `build`")));
  }

  @Test
  void emptyStagesListProducesAtLeastOneStageError() throws IOException {
    // Confirm the error message text
    String yaml = """
        pipeline:
          name: test
        stages: []
        compile:
          stage: build
          image: alpine
          script: echo hi
        """;

    List<String> errors = validate(yaml);
    assertTrue(errors.stream().anyMatch(e -> e.contains("at least 1 stage must be defined")));
  }

  // ── Job rules ────────────────────────────────────────────────────────────────

  @Test
  void jobReferencesUndefinedStage() throws IOException {
    String yaml = """
        pipeline:
          name: test
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo
        orphan:
          stage: nonexistent
          image: alpine
          script: echo
        """;

    List<String> errors = validate(yaml);
    assertTrue(errors.stream().anyMatch(e ->
        e.contains("references undefined stage `nonexistent`")));
  }

  // ── Needs rules ──────────────────────────────────────────────────────────────

  @Test
  void needsNotExist() throws IOException {
    String yaml = """
        pipeline:
          name: test
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo
          needs:
            - notexist
        """;

    List<String> errors = validate(yaml);
    assertTrue(errors.stream().anyMatch(e -> e.contains("does not exist")));
  }

  @Test
  void duplicateNeeds() throws IOException {
    String yaml = """
        pipeline:
          name: test
        stages:
          - build
        jobA:
          stage: build
          image: alpine
          script: echo A
        jobB:
          stage: build
          image: alpine
          script: echo B
          needs:
            - jobA
            - jobA
        """;

    List<String> errors = validate(yaml);
    assertTrue(errors.stream().anyMatch(e -> e.contains("duplicate entry `jobA` in `needs`")));
  }

  @Test
  void needsReferencesDifferentStageJob() throws IOException {
    // A job's needs must be in the same stage - referencing a job in another
    // stage is reported as "does not exist" (cross-stage needs not validated
    // specially, but the job must at least exist in the pipeline)
    String yaml = """
        pipeline:
          name: test
        stages:
          - build
          - test
        compile:
          stage: build
          image: alpine
          script: echo
        unittest:
          stage: test
          image: alpine
          script: echo
          needs:
            - missingjob
        """;

    List<String> errors = validate(yaml);
    assertTrue(errors.stream().anyMatch(e -> e.contains("does not exist")));
  }

  // ── Cycle detection ──────────────────────────────────────────────────────────

  @Test
  void simpleCycleTwoJobs() throws IOException {
    String yaml = """
        pipeline:
          name: test
        stages:
          - build
        job1:
          stage: build
          image: alpine
          script: echo
          needs:
            - job2
        job2:
          stage: build
          image: alpine
          script: echo
          needs:
            - job1
        """;

    List<String> errors = validate(yaml);
    assertTrue(errors.stream().anyMatch(e -> e.contains("cycle detected")));
  }

  @Test
  void threeNodeCycle() throws IOException {
    String yaml = """
        pipeline:
          name: test
        stages:
          - build
        job1:
          stage: build
          image: alpine
          script: echo
          needs:
            - job3
        job2:
          stage: build
          image: alpine
          script: echo
          needs:
            - job1
        job3:
          stage: build
          image: alpine
          script: echo
          needs:
            - job2
        """;

    List<String> errors = validate(yaml);
    assertTrue(errors.stream().anyMatch(e -> e.contains("cycle detected")));
  }

  @Test
  void selfReferencingNeedsCycle() throws IOException {
    // A job that lists itself in needs
    Pipeline p = new Pipeline();
    p.name = "test";
    p.stages = new ArrayList<>(List.of("build"));
    Job j = new Job();
    j.name = "selfref";
    j.stage = "build";
    j.needs = new ArrayList<>(List.of("selfref"));
    j.line = 5;
    j.col = 1;
    j.needsLine = 7;
    j.needsCol = 3;
    p.jobs.put("selfref", j);

    Validator v = new Validator("file.yaml", p);
    List<String> errors = v.validate();
    // Self-reference: needs contains itself but "selfref" is a valid job name,
    // so no "does not exist" error, but a cycle is detected.
    assertTrue(errors.stream().anyMatch(e ->
        e.contains("cycle detected") || e.contains("selfref")));
  }

  @Test
  void noCycleDiamondDependency() throws IOException {
    // Diamond: A -> B, A -> C, B -> D, C -> D (no cycle)
    String yaml = """
        pipeline:
          name: test
        stages:
          - build
        A:
          stage: build
          image: alpine
          script: echo A
        B:
          stage: build
          image: alpine
          script: echo B
          needs:
            - A
        C:
          stage: build
          image: alpine
          script: echo C
          needs:
            - A
        D:
          stage: build
          image: alpine
          script: echo D
          needs:
            - B
            - C
        """;

    List<String> errors = validate(yaml);
    assertFalse(errors.stream().anyMatch(e -> e.contains("cycle detected")));
    assertTrue(errors.isEmpty());
  }

  @Test
  void cycleReportedOnlyOnce() throws IOException {
    // Even if multiple cycles exist, at most one is reported (first detected)
    String yaml = """
        pipeline:
          name: test
        stages:
          - build
        job1:
          stage: build
          image: alpine
          script: echo
          needs:
            - job2
        job2:
          stage: build
          image: alpine
          script: echo
          needs:
            - job1
        """;

    List<String> errors = validate(yaml);
    long cycleCount = errors.stream()
        .filter(e -> e.contains("cycle detected")).count();
    assertEquals(1, cycleCount);
  }

  // ── Error format ─────────────────────────────────────────────────────────────

  @Test
  void errorContainsFilename() throws IOException {
    String yaml = """
        pipeline:
          name: test
        stages: []
        """;

    Path f = tmp.resolve("mypipeline.yaml");
    Files.writeString(f, yaml);

    YamlParser parser = new YamlParser(f.toString());
    Pipeline p = parser.parse();
    Validator v = new Validator(f.toString(), p);
    List<String> errors = v.validate();

    assertFalse(errors.isEmpty());
    assertTrue(errors.get(0).startsWith(f.toString()));
  }

  @Test
  void multipleErrorsAllReported() throws IOException {
    // Two empty stages: both should be reported
    String yaml = """
        pipeline:
          name: test
        stages:
          - build
          - test
          - docs
        jobA:
          stage: build
          image: alpine
          script: echo A
        """;

    List<String> errors = validate(yaml);
    long emptyCount = errors.stream()
        .filter(e -> e.contains("has no jobs")).count();
    // 'test' and 'docs' both have no jobs
    assertEquals(2, emptyCount);
  }

  // ── Helper ──────────────────────────────────────────────────────────────────

  private List<String> validate(String yaml) throws IOException {
    Path f = tmp.resolve("p.yaml");
    Files.writeString(f, yaml);

    YamlParser parser = new YamlParser(f.toString());
    Pipeline p = parser.parse();

    List<String> errors = new ArrayList<>(parser.getErrors());
    if (p != null) {
      Validator v = new Validator(f.toString(), p);
      errors.addAll(v.validate());
    }
    return errors;
  }
}

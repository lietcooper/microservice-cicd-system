package cicd.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class ValidatorTest {

  @TempDir
  Path tmp;

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
    Validator vv = new Validator("file.yaml", null);
    List<String> errors = vv.validate();
    assertTrue(errors.isEmpty());
  }

  @Test
  void noStages() throws IOException {
    String yaml = """
        pipeline:
          name: test
        stages: []
        """;

    List<String> errors = validate(yaml);
    assertTrue(errors.stream().anyMatch(
        ee -> ee.contains("at least 1 stage")));
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
    assertTrue(errors.stream().anyMatch(
        ee -> ee.contains("stage `test` has no jobs")));
  }

  @Test
  void duplicateStageName() throws IOException {
    Pipeline pp = new Pipeline();
    pp.name = "test";
    pp.stages = new ArrayList<>(
        List.of("build", "build", "test"));
    Job j1 = new Job();
    j1.name = "compile";
    j1.stage = "build";
    pp.jobs.put("compile", j1);
    Job j2 = new Job();
    j2.name = "unittest";
    j2.stage = "test";
    pp.jobs.put("unittest", j2);

    Validator vv = new Validator("file.yaml", pp);
    List<String> errors = vv.validate();
    assertTrue(errors.stream().anyMatch(
        ee -> ee.contains("duplicate stage name `build`")));
  }

  @Test
  void emptyStagesListProducesAtLeastOneStageError()
      throws IOException {
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
    assertTrue(errors.stream().anyMatch(
        ee -> ee.contains("at least 1 stage must be defined")));
  }

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
    assertTrue(errors.stream().anyMatch(ee ->
        ee.contains("references undefined stage `nonexistent`")));
  }

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
    assertTrue(errors.stream().anyMatch(
        ee -> ee.contains("does not exist")));
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
    assertTrue(errors.stream().anyMatch(
        ee -> ee.contains("duplicate entry `jobA` in `needs`")));
  }

  @Test
  void needsReferencesDifferentStageJob() throws IOException {
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
    assertTrue(errors.stream().anyMatch(
        ee -> ee.contains("does not exist")));
  }

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
    assertTrue(errors.stream().anyMatch(
        ee -> ee.contains("cycle detected")));
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
    assertTrue(errors.stream().anyMatch(
        ee -> ee.contains("cycle detected")));
  }

  @Test
  void selfReferencingNeedsCycle() throws IOException {
    Pipeline pp = new Pipeline();
    pp.name = "test";
    pp.stages = new ArrayList<>(List.of("build"));
    Job jj = new Job();
    jj.name = "selfref";
    jj.stage = "build";
    jj.needs = new ArrayList<>(List.of("selfref"));
    jj.line = 5;
    jj.col = 1;
    jj.needsLine = 7;
    jj.needsCol = 3;
    pp.jobs.put("selfref", jj);

    Validator vv = new Validator("file.yaml", pp);
    List<String> errors = vv.validate();
    assertTrue(errors.stream().anyMatch(ee ->
        ee.contains("cycle detected")
            || ee.contains("selfref")));
  }

  @Test
  void noCycleDiamondDependency() throws IOException {
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
    assertFalse(errors.stream().anyMatch(
        ee -> ee.contains("cycle detected")));
    assertTrue(errors.isEmpty());
  }

  @Test
  void cycleReportedOnlyOnce() throws IOException {
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
        .filter(ee -> ee.contains("cycle detected")).count();
    assertEquals(1, cycleCount);
  }

  @Test
  void errorContainsFilename() throws IOException {
    String yaml = """
        pipeline:
          name: test
        stages: []
        """;

    Path ff = tmp.resolve("mypipeline.yaml");
    Files.writeString(ff, yaml);

    YamlParser parser = new YamlParser(ff.toString());
    Pipeline pp = parser.parse();
    Validator vv = new Validator(ff.toString(), pp);
    List<String> errors = vv.validate();

    assertFalse(errors.isEmpty());
    assertTrue(errors.get(0).startsWith(ff.toString()));
  }

  @Test
  void multipleErrorsAllReported() throws IOException {
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
        .filter(ee -> ee.contains("has no jobs")).count();
    assertEquals(2, emptyCount);
  }

  private List<String> validate(String yaml) throws IOException {
    Path ff = tmp.resolve("p.yaml");
    Files.writeString(ff, yaml);

    YamlParser parser = new YamlParser(ff.toString());
    Pipeline pp = parser.parse();

    List<String> errors = new ArrayList<>(parser.getErrors());
    if (pp != null) {
      Validator vv = new Validator(ff.toString(), pp);
      errors.addAll(vv.validate());
    }
    return errors;
  }
}

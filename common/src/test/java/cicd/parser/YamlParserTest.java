package cicd.parser;

import cicd.model.Job;
import cicd.model.Pipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class YamlParserTest {

  @TempDir
  Path tmp;

  // ── Happy-path parsing ───────────────────────────────────────────────────────

  @Test
  void parseValid() throws IOException {
    String yaml = """
        pipeline:
          name: test
          description: a test
        stages:
          - build
          - test
        compile:
          stage: build
          image: gradle:jdk17
          script: ./gradlew build
        unittest:
          stage: test
          image: gradle:jdk17
          script:
            - ./gradlew test
        """;

    Path f = tmp.resolve("p.yaml");
    Files.writeString(f, yaml);

    YamlParser parser = new YamlParser(f.toString());
    Pipeline p = parser.parse();

    assertTrue(parser.getErrors().isEmpty());
    assertEquals("test", p.name);
    assertEquals("a test", p.desc);
    assertEquals(2, p.stages.size());
    assertEquals("build", p.stages.get(0));
    assertEquals("test", p.stages.get(1));
    assertEquals(2, p.jobs.size());
  }

  @Test
  void parseScriptAsSingleString() throws IOException {
    String yaml = """
        pipeline:
          name: test
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo hello
        """;

    Path f = tmp.resolve("p.yaml");
    Files.writeString(f, yaml);

    YamlParser parser = new YamlParser(f.toString());
    Pipeline p = parser.parse();

    assertTrue(parser.getErrors().isEmpty());
    Job job = p.jobs.get("compile");
    assertNotNull(job);
    assertEquals(1, job.script.size());
    assertEquals("echo hello", job.script.get(0));
  }

  @Test
  void parseScriptAsList() throws IOException {
    String yaml = """
        pipeline:
          name: test
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script:
            - echo step1
            - echo step2
            - echo step3
        """;

    Path f = tmp.resolve("p.yaml");
    Files.writeString(f, yaml);

    YamlParser parser = new YamlParser(f.toString());
    Pipeline p = parser.parse();

    assertTrue(parser.getErrors().isEmpty());
    Job job = p.jobs.get("compile");
    assertEquals(3, job.script.size());
    assertEquals("echo step1", job.script.get(0));
    assertEquals("echo step2", job.script.get(1));
    assertEquals("echo step3", job.script.get(2));
  }

  @Test
  void parseNeeds() throws IOException {
    String yaml = """
        pipeline:
          name: test
        stages:
          - test
        job1:
          stage: test
          image: alpine
          script: echo 1
        job2:
          stage: test
          image: alpine
          script: echo 2
          needs:
            - job1
        """;

    Path f = tmp.resolve("p.yaml");
    Files.writeString(f, yaml);

    YamlParser parser = new YamlParser(f.toString());
    Pipeline p = parser.parse();

    assertTrue(parser.getErrors().isEmpty());
    assertEquals(1, p.jobs.get("job2").needs.size());
    assertEquals("job1", p.jobs.get("job2").needs.get(0));
  }

  @Test
  void parseNeedsMultiple() throws IOException {
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
        C:
          stage: build
          image: alpine
          script: echo C
          needs:
            - A
            - B
        """;

    Path f = tmp.resolve("p.yaml");
    Files.writeString(f, yaml);

    YamlParser parser = new YamlParser(f.toString());
    Pipeline p = parser.parse();

    assertTrue(parser.getErrors().isEmpty());
    assertEquals(2, p.jobs.get("C").needs.size());
    assertTrue(p.jobs.get("C").needs.contains("A"));
    assertTrue(p.jobs.get("C").needs.contains("B"));
  }

  @Test
  void parsePipelineNameLineAndCol() throws IOException {
    String yaml = """
        pipeline:
          name: myapp
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo
        """;

    Path f = tmp.resolve("p.yaml");
    Files.writeString(f, yaml);

    YamlParser parser = new YamlParser(f.toString());
    Pipeline p = parser.parse();

    assertTrue(parser.getErrors().isEmpty());
    // nameLine and nameCol are set from the YAML node position
    assertTrue(p.nameLine > 0);
    assertTrue(p.nameCol > 0);
  }

  @Test
  void parseJobLineAndCol() throws IOException {
    String yaml = """
        pipeline:
          name: test
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo
        """;

    Path f = tmp.resolve("p.yaml");
    Files.writeString(f, yaml);

    YamlParser parser = new YamlParser(f.toString());
    Pipeline p = parser.parse();

    assertTrue(parser.getErrors().isEmpty());
    Job job = p.jobs.get("compile");
    assertTrue(job.line > 0);
    assertTrue(job.col > 0);
  }

  @Test
  void parseJobDefinedAsSequenceOfMappings() throws IOException {
    // The example in the spec uses list-style job definitions
    String yaml = """
        pipeline:
          name: test
        stages:
          - build
        compile:
          - stage: build
          - image: gradle:jdk21-corretto
          - script: 'gradle build'
        """;

    Path f = tmp.resolve("p.yaml");
    Files.writeString(f, yaml);

    YamlParser parser = new YamlParser(f.toString());
    Pipeline p = parser.parse();

    assertTrue(parser.getErrors().isEmpty());
    assertNotNull(p.jobs.get("compile"));
    assertEquals("build", p.jobs.get("compile").stage);
    assertEquals("gradle:jdk21-corretto", p.jobs.get("compile").image);
  }

  @Test
  void parseDefaultStagesNotInFile() throws IOException {
    // If stages key is absent, stages list stays empty (default must be
    // applied by caller)
    String yaml = """
        pipeline:
          name: test
        compile:
          stage: build
          image: alpine
          script: echo
        """;

    Path f = tmp.resolve("p.yaml");
    Files.writeString(f, yaml);

    YamlParser parser = new YamlParser(f.toString());
    Pipeline p = parser.parse();

    // No parse error, but stages list is empty
    assertTrue(parser.getErrors().isEmpty());
    assertTrue(p.stages.isEmpty());
  }

  // ── Error cases ─────────────────────────────────────────────────────────────

  @Test
  void parseNotFound() {
    YamlParser parser = new YamlParser("/no/such/file.yaml");
    parser.parse();
    assertFalse(parser.getErrors().isEmpty());
    assertTrue(parser.getErrors().get(0).contains("file not found"));
  }

  @Test
  void parseEmpty() throws IOException {
    Path f = tmp.resolve("empty.yaml");
    Files.writeString(f, "");

    YamlParser parser = new YamlParser(f.toString());
    parser.parse();
    assertFalse(parser.getErrors().isEmpty());
    assertTrue(parser.getErrors().get(0).contains("empty or invalid YAML"));
  }

  @Test
  void parseScalarRootIsInvalid() throws IOException {
    Path f = tmp.resolve("scalar.yaml");
    Files.writeString(f, "just a string");

    YamlParser parser = new YamlParser(f.toString());
    Pipeline p = parser.parse();
    assertFalse(parser.getErrors().isEmpty());
    assertNull(p);
  }

  @Test
  void parseIntegerNameError() throws IOException {
    // pipeline.name must be a string, not an integer
    String yaml = """
        pipeline:
          name: 3
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo
        """;

    Path f = tmp.resolve("p.yaml");
    Files.writeString(f, yaml);

    YamlParser parser = new YamlParser(f.toString());
    Pipeline p = parser.parse();

    assertFalse(parser.getErrors().isEmpty());
    assertTrue(parser.getErrors().stream().anyMatch(
        e -> e.contains("wrong type") && e.contains("name")));
  }

  @Test
  void parseBooleanDescriptionError() throws IOException {
    String yaml = """
        pipeline:
          name: test
          description: true
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo
        """;

    Path f = tmp.resolve("p.yaml");
    Files.writeString(f, yaml);

    YamlParser parser = new YamlParser(f.toString());
    parser.parse();

    assertFalse(parser.getErrors().isEmpty());
    assertTrue(parser.getErrors().stream().anyMatch(
        e -> e.contains("description")));
  }

  @Test
  void parseStagesAsScalarError() throws IOException {
    String yaml = """
        pipeline:
          name: test
        stages: notalist
        compile:
          stage: build
          image: alpine
          script: echo
        """;

    Path f = tmp.resolve("p.yaml");
    Files.writeString(f, yaml);

    YamlParser parser = new YamlParser(f.toString());
    parser.parse();

    assertFalse(parser.getErrors().isEmpty());
    assertTrue(parser.getErrors().stream().anyMatch(
        e -> e.contains("stages")));
  }

  @Test
  void parseNeedsAsScalarError() throws IOException {
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
          needs: jobA
        """;

    Path f = tmp.resolve("p.yaml");
    Files.writeString(f, yaml);

    YamlParser parser = new YamlParser(f.toString());
    parser.parse();

    assertFalse(parser.getErrors().isEmpty());
    assertTrue(parser.getErrors().stream().anyMatch(
        e -> e.contains("needs")));
  }

  @Test
  void parseErrorContainsFilenameAndLineCol() throws IOException {
    String yaml = """
        pipeline:
          name: 99
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo
        """;

    Path f = tmp.resolve("errfile.yaml");
    Files.writeString(f, yaml);

    YamlParser parser = new YamlParser(f.toString());
    parser.parse();

    assertFalse(parser.getErrors().isEmpty());
    // Error should have format: filename:line:col: message
    String err = parser.getErrors().get(0);
    assertTrue(err.startsWith(f.toString()));
    // Should contain colon-separated line:col
    assertTrue(err.matches(".+:\\d+:\\d+:.+"));
  }

  @Test
  void parseImageAsListError() throws IOException {
    // Image must be a string, not a list
    String yaml = """
        pipeline:
          name: test
        stages:
          - build
        compile:
          stage: build
          image:
            - alpine
            - gradle
          script: echo
        """;

    Path f = tmp.resolve("p.yaml");
    Files.writeString(f, yaml);

    YamlParser parser = new YamlParser(f.toString());
    parser.parse();

    assertFalse(parser.getErrors().isEmpty());
    assertTrue(parser.getErrors().stream().anyMatch(
        e -> e.contains("image")));
  }

  @Test
  void parseNeedsLineAndColAreRecorded() throws IOException {
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

    Path f = tmp.resolve("p.yaml");
    Files.writeString(f, yaml);

    YamlParser parser = new YamlParser(f.toString());
    Pipeline p = parser.parse();

    assertTrue(parser.getErrors().isEmpty());
    Job jobB = p.jobs.get("jobB");
    assertTrue(jobB.needsLine > 0);
    assertTrue(jobB.needsCol > 0);
  }

  @Test
  void getErrorsReturnsEmptyBeforeParse() {
    YamlParser parser = new YamlParser("/any/path.yaml");
    // Before calling parse(), errors list is empty
    assertTrue(parser.getErrors().isEmpty());
  }

  @Test
  void parsePipelineNameAbsent() throws IOException {
    // Pipeline block with no name key
    String yaml = """
        pipeline:
          description: no name here
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo
        """;

    Path f = tmp.resolve("p.yaml");
    Files.writeString(f, yaml);

    YamlParser parser = new YamlParser(f.toString());
    Pipeline p = parser.parse();

    assertTrue(parser.getErrors().isEmpty());
    assertNull(p.name);
  }
}

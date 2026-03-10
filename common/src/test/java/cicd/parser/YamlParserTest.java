package cicd.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cicd.model.Job;
import cicd.model.Pipeline;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlParserTest {

  @TempDir
  Path tmp;

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

    Path ff = tmp.resolve("p.yaml");
    Files.writeString(ff, yaml);

    YamlParser parser = new YamlParser(ff.toString());
    Pipeline pp = parser.parse();

    assertTrue(parser.getErrors().isEmpty());
    assertEquals("test", pp.name);
    assertEquals("a test", pp.desc);
    assertEquals(2, pp.stages.size());
    assertEquals("build", pp.stages.get(0));
    assertEquals("test", pp.stages.get(1));
    assertEquals(2, pp.jobs.size());
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

    Path ff = tmp.resolve("p.yaml");
    Files.writeString(ff, yaml);

    YamlParser parser = new YamlParser(ff.toString());
    Pipeline pp = parser.parse();

    assertTrue(parser.getErrors().isEmpty());
    Job job = pp.jobs.get("compile");
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

    Path ff = tmp.resolve("p.yaml");
    Files.writeString(ff, yaml);

    YamlParser parser = new YamlParser(ff.toString());
    Pipeline pp = parser.parse();

    assertTrue(parser.getErrors().isEmpty());
    Job job = pp.jobs.get("compile");
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

    Path ff = tmp.resolve("p.yaml");
    Files.writeString(ff, yaml);

    YamlParser parser = new YamlParser(ff.toString());
    Pipeline pp = parser.parse();

    assertTrue(parser.getErrors().isEmpty());
    assertEquals(1, pp.jobs.get("job2").needs.size());
    assertEquals("job1", pp.jobs.get("job2").needs.get(0));
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

    Path ff = tmp.resolve("p.yaml");
    Files.writeString(ff, yaml);

    YamlParser parser = new YamlParser(ff.toString());
    Pipeline pp = parser.parse();

    assertTrue(parser.getErrors().isEmpty());
    assertEquals(2, pp.jobs.get("C").needs.size());
    assertTrue(pp.jobs.get("C").needs.contains("A"));
    assertTrue(pp.jobs.get("C").needs.contains("B"));
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

    Path ff = tmp.resolve("p.yaml");
    Files.writeString(ff, yaml);

    YamlParser parser = new YamlParser(ff.toString());
    Pipeline pp = parser.parse();

    assertTrue(parser.getErrors().isEmpty());
    assertTrue(pp.nameLine > 0);
    assertTrue(pp.nameCol > 0);
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

    Path ff = tmp.resolve("p.yaml");
    Files.writeString(ff, yaml);

    YamlParser parser = new YamlParser(ff.toString());
    Pipeline pp = parser.parse();

    assertTrue(parser.getErrors().isEmpty());
    Job job = pp.jobs.get("compile");
    assertTrue(job.line > 0);
    assertTrue(job.col > 0);
  }

  @Test
  void parseJobDefinedAsSequenceOfMappings() throws IOException {
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

    Path ff = tmp.resolve("p.yaml");
    Files.writeString(ff, yaml);

    YamlParser parser = new YamlParser(ff.toString());
    Pipeline pp = parser.parse();

    assertTrue(parser.getErrors().isEmpty());
    assertNotNull(pp.jobs.get("compile"));
    assertEquals("build", pp.jobs.get("compile").stage);
    assertEquals("gradle:jdk21-corretto",
        pp.jobs.get("compile").image);
  }

  @Test
  void parseDefaultStagesNotInFile() throws IOException {
    String yaml = """
        pipeline:
          name: test
        compile:
          stage: build
          image: alpine
          script: echo
        """;

    Path ff = tmp.resolve("p.yaml");
    Files.writeString(ff, yaml);

    YamlParser parser = new YamlParser(ff.toString());
    Pipeline pp = parser.parse();

    assertTrue(parser.getErrors().isEmpty());
    assertTrue(pp.stages.isEmpty());
  }

  @Test
  void parseNotFound() {
    YamlParser parser = new YamlParser("/no/such/file.yaml");
    parser.parse();
    assertFalse(parser.getErrors().isEmpty());
    assertTrue(
        parser.getErrors().get(0).contains("file not found"));
  }

  @Test
  void parseEmpty() throws IOException {
    Path ff = tmp.resolve("empty.yaml");
    Files.writeString(ff, "");

    YamlParser parser = new YamlParser(ff.toString());
    parser.parse();
    assertFalse(parser.getErrors().isEmpty());
    assertTrue(parser.getErrors().get(0).contains(
        "empty or invalid YAML"));
  }

  @Test
  void parseScalarRootIsInvalid() throws IOException {
    Path ff = tmp.resolve("scalar.yaml");
    Files.writeString(ff, "just a string");

    YamlParser parser = new YamlParser(ff.toString());
    Pipeline pp = parser.parse();
    assertFalse(parser.getErrors().isEmpty());
    assertNull(pp);
  }

  @Test
  void parseIntegerNameError() throws IOException {
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

    Path ff = tmp.resolve("p.yaml");
    Files.writeString(ff, yaml);

    YamlParser parser = new YamlParser(ff.toString());
    parser.parse();

    assertFalse(parser.getErrors().isEmpty());
    assertTrue(parser.getErrors().stream().anyMatch(
        ee -> ee.contains("wrong type") && ee.contains("name")));
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

    Path ff = tmp.resolve("p.yaml");
    Files.writeString(ff, yaml);

    YamlParser parser = new YamlParser(ff.toString());
    parser.parse();

    assertFalse(parser.getErrors().isEmpty());
    assertTrue(parser.getErrors().stream().anyMatch(
        ee -> ee.contains("description")));
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

    Path ff = tmp.resolve("p.yaml");
    Files.writeString(ff, yaml);

    YamlParser parser = new YamlParser(ff.toString());
    parser.parse();

    assertFalse(parser.getErrors().isEmpty());
    assertTrue(parser.getErrors().stream().anyMatch(
        ee -> ee.contains("stages")));
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

    Path ff = tmp.resolve("p.yaml");
    Files.writeString(ff, yaml);

    YamlParser parser = new YamlParser(ff.toString());
    parser.parse();

    assertFalse(parser.getErrors().isEmpty());
    assertTrue(parser.getErrors().stream().anyMatch(
        ee -> ee.contains("needs")));
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

    Path ff = tmp.resolve("errfile.yaml");
    Files.writeString(ff, yaml);

    YamlParser parser = new YamlParser(ff.toString());
    parser.parse();

    assertFalse(parser.getErrors().isEmpty());
    String err = parser.getErrors().get(0);
    assertTrue(err.startsWith(ff.toString()));
    assertTrue(err.matches(".+:\\d+:\\d+:.+"));
  }

  @Test
  void parseImageAsListError() throws IOException {
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

    Path ff = tmp.resolve("p.yaml");
    Files.writeString(ff, yaml);

    YamlParser parser = new YamlParser(ff.toString());
    parser.parse();

    assertFalse(parser.getErrors().isEmpty());
    assertTrue(parser.getErrors().stream().anyMatch(
        ee -> ee.contains("image")));
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

    Path ff = tmp.resolve("p.yaml");
    Files.writeString(ff, yaml);

    YamlParser parser = new YamlParser(ff.toString());
    Pipeline pp = parser.parse();

    assertTrue(parser.getErrors().isEmpty());
    Job jobB = pp.jobs.get("jobB");
    assertTrue(jobB.needsLine > 0);
    assertTrue(jobB.needsCol > 0);
  }

  @Test
  void getErrorsReturnsEmptyBeforeParse() {
    YamlParser parser = new YamlParser("/any/path.yaml");
    assertTrue(parser.getErrors().isEmpty());
  }

  @Test
  void parsePipelineNameAbsent() throws IOException {
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

    Path ff = tmp.resolve("p.yaml");
    Files.writeString(ff, yaml);

    YamlParser parser = new YamlParser(ff.toString());
    Pipeline pp = parser.parse();

    assertTrue(parser.getErrors().isEmpty());
    assertNull(pp.name);
  }
}

package cicd.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cicd.model.Job;
import cicd.model.Pipeline;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests that the YAML parser correctly handles the artifacts key. */
class YamlParserArtifactsTest {

  @TempDir
  Path tmp;

  @Test
  void parsesArtifactsList() throws IOException {
    String yaml = """
        pipeline:
          name: test
        stages:
          - build
        compile:
          - stage: build
          - image: gradle:jdk21
          - script: 'gradle build'
          - artifacts:
            - build/distribution/app.jar
            - build/reports/*.html
            - build/*/docs/
            - build/**/distribution
        """;

    Path ff = tmp.resolve("p.yaml");
    Files.writeString(ff, yaml);

    YamlParser parser = new YamlParser(ff.toString());
    Pipeline pp = parser.parse();

    assertTrue(parser.getErrors().isEmpty(),
        "Unexpected errors: " + parser.getErrors());
    assertNotNull(pp);

    Job compile = pp.jobs.get("compile");
    assertNotNull(compile);
    assertEquals(4, compile.artifacts.size());
    assertEquals("build/distribution/app.jar", compile.artifacts.get(0));
    assertEquals("build/reports/*.html", compile.artifacts.get(1));
    assertEquals("build/*/docs/", compile.artifacts.get(2));
    assertEquals("build/**/distribution", compile.artifacts.get(3));
  }

  @Test
  void emptyArtifactsWhenNotSpecified() throws IOException {
    String yaml = """
        pipeline:
          name: test
        stages:
          - build
        compile:
          - stage: build
          - image: gradle:jdk21
          - script: 'gradle build'
        """;

    Path ff = tmp.resolve("p.yaml");
    Files.writeString(ff, yaml);

    YamlParser parser = new YamlParser(ff.toString());
    Pipeline pp = parser.parse();

    assertTrue(parser.getErrors().isEmpty());
    Job compile = pp.jobs.get("compile");
    assertNotNull(compile);
    assertTrue(compile.artifacts.isEmpty());
  }

  @Test
  void invalidArtifactsTypeProducesError() throws IOException {
    String yaml = """
        pipeline:
          name: test
        stages:
          - build
        compile:
          - stage: build
          - image: gradle:jdk21
          - script: 'gradle build'
          - artifacts: not-a-list
        """;

    Path ff = tmp.resolve("p.yaml");
    Files.writeString(ff, yaml);

    YamlParser parser = new YamlParser(ff.toString());
    parser.parse();

    assertTrue(parser.getErrors().stream()
        .anyMatch(e -> e.contains("artifacts")),
        "Expected an error about artifacts type");
  }

  @Test
  void multipleJobsWithArtifacts() throws IOException {
    String yaml = """
        pipeline:
          name: test
        stages:
          - build
          - test
        compile:
          - stage: build
          - image: gradle:jdk21
          - script: 'gradle build'
          - artifacts:
            - build/libs/*.jar
        statics:
          - stage: test
          - image: gradle:jdk21
          - script: 'gradle check'
          - artifacts:
            - build/reports/*.html
            - build/*/docs/
        """;

    Path ff = tmp.resolve("p.yaml");
    Files.writeString(ff, yaml);

    YamlParser parser = new YamlParser(ff.toString());
    Pipeline pp = parser.parse();

    assertTrue(parser.getErrors().isEmpty());

    Job compile = pp.jobs.get("compile");
    assertEquals(List.of("build/libs/*.jar"), compile.artifacts);

    Job statics = pp.jobs.get("statics");
    assertEquals(2, statics.artifacts.size());
  }
}

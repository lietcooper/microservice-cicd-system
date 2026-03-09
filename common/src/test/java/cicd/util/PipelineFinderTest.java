package cicd.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PipelineFinderTest {

  @TempDir
  Path tmp;

  // ── Tests using the real project's .pipelines/ directory ────────────────────

  @Test
  void testFindExisting() {
    String repoRoot = System.getProperty("user.dir");
    var result = PipelineFinder.findByName("default", repoRoot);
    assertFalse(result.hasError());
    assertEquals("default", result.pipeline.name);
    assertNotNull(result.filePath);
  }

  @Test
  void testFindNonExisting() {
    String repoRoot = System.getProperty("user.dir");
    var result = PipelineFinder.findByName("nonexistent-pipeline-xyz", repoRoot);
    assertTrue(result.hasError());
    assertNull(result.pipeline);
    assertNull(result.filePath);
    assertTrue(result.error.contains("no pipeline found with name"));
  }

  // ── Tests using temporary directories ───────────────────────────────────────

  @Test
  void testFindWhenPipelinesDirectoryMissing() {
    // tmp has no .pipelines subdirectory
    var result = PipelineFinder.findByName("default", tmp.toString());
    assertTrue(result.hasError());
    assertTrue(result.error.contains(".pipelines/ directory not found"));
  }

  @Test
  void testFindWhenPipelinesDirectoryEmpty() throws IOException {
    Path pipelinesDir = tmp.resolve(".pipelines");
    Files.createDirectory(pipelinesDir);

    var result = PipelineFinder.findByName("default", tmp.toString());
    assertTrue(result.hasError());
    assertTrue(result.error.contains("no YAML files found"));
  }

  @Test
  void testFindWithYamlExtension() throws IOException {
    Path pipelinesDir = tmp.resolve(".pipelines");
    Files.createDirectory(pipelinesDir);

    String yaml = """
        pipeline:
          name: mypipeline
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo hi
        """;
    Files.writeString(pipelinesDir.resolve("mypipeline.yaml"), yaml);

    var result = PipelineFinder.findByName("mypipeline", tmp.toString());
    assertFalse(result.hasError());
    assertEquals("mypipeline", result.pipeline.name);
    assertNotNull(result.filePath);
  }

  @Test
  void testFindWithYmlExtension() throws IOException {
    Path pipelinesDir = tmp.resolve(".pipelines");
    Files.createDirectory(pipelinesDir);

    String yaml = """
        pipeline:
          name: otherpipeline
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo hi
        """;
    Files.writeString(pipelinesDir.resolve("otherpipeline.yml"), yaml);

    var result = PipelineFinder.findByName("otherpipeline", tmp.toString());
    assertFalse(result.hasError());
    assertEquals("otherpipeline", result.pipeline.name);
  }

  @Test
  void testFindCorrectPipelineAmongMultiple() throws IOException {
    Path pipelinesDir = tmp.resolve(".pipelines");
    Files.createDirectory(pipelinesDir);

    String yaml1 = """
        pipeline:
          name: alpha
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo alpha
        """;
    String yaml2 = """
        pipeline:
          name: beta
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo beta
        """;

    Files.writeString(pipelinesDir.resolve("alpha.yaml"), yaml1);
    Files.writeString(pipelinesDir.resolve("beta.yaml"), yaml2);

    var resultAlpha = PipelineFinder.findByName("alpha", tmp.toString());
    assertFalse(resultAlpha.hasError());
    assertEquals("alpha", resultAlpha.pipeline.name);

    var resultBeta = PipelineFinder.findByName("beta", tmp.toString());
    assertFalse(resultBeta.hasError());
    assertEquals("beta", resultBeta.pipeline.name);
  }

  @Test
  void testFindSkipsFilesWithParseErrors() throws IOException {
    Path pipelinesDir = tmp.resolve(".pipelines");
    Files.createDirectory(pipelinesDir);

    // Invalid YAML file (no pipeline block, empty)
    Files.writeString(pipelinesDir.resolve("broken.yaml"), "");

    // Valid YAML file
    String yaml = """
        pipeline:
          name: valid
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo
        """;
    Files.writeString(pipelinesDir.resolve("valid.yaml"), yaml);

    var result = PipelineFinder.findByName("valid", tmp.toString());
    assertFalse(result.hasError());
    assertEquals("valid", result.pipeline.name);
  }

  @Test
  void testFindReturnsErrorWhenNotFound() throws IOException {
    Path pipelinesDir = tmp.resolve(".pipelines");
    Files.createDirectory(pipelinesDir);

    String yaml = """
        pipeline:
          name: existing
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo
        """;
    Files.writeString(pipelinesDir.resolve("existing.yaml"), yaml);

    var result = PipelineFinder.findByName("notexisting", tmp.toString());
    assertTrue(result.hasError());
    assertTrue(result.error.contains("no pipeline found with name 'notexisting'"));
  }

  // ── FindResult inner class tests ─────────────────────────────────────────────

  @Test
  void testFindResultHasErrorWhenErrorPresent() throws IOException {
    var result = PipelineFinder.findByName("x", tmp.toString());
    assertTrue(result.hasError());
    assertNotNull(result.error);
    assertNull(result.pipeline);
    assertNull(result.filePath);
  }

  @Test
  void testFindResultNoErrorOnSuccess() throws IOException {
    Path pipelinesDir = tmp.resolve(".pipelines");
    Files.createDirectory(pipelinesDir);

    String yaml = """
        pipeline:
          name: success
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo
        """;
    Files.writeString(pipelinesDir.resolve("success.yaml"), yaml);

    var result = PipelineFinder.findByName("success", tmp.toString());
    assertFalse(result.hasError());
    assertNull(result.error);
    assertNotNull(result.pipeline);
    assertNotNull(result.filePath);
  }
}

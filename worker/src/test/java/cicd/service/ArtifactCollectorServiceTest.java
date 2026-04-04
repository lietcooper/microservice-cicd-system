package cicd.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for ArtifactCollectorService pattern matching. */
class ArtifactCollectorServiceTest {

  @TempDir
  Path workspace;

  private ArtifactCollectorService collector;

  @BeforeEach
  void setUp() throws IOException {
    collector = new ArtifactCollectorService();

    // Create test file structure:
    // README.md
    // build/distribution/app.jar
    // build/reports/checkstyle.html
    // build/reports/test.html
    // build/java/doc/index.html
    // build/python/doc/readme.txt
    // build/java/reports/doc/deep.txt
    // src/Main.java
    // src/Test.java
    createFile("README.md");
    createFile("build/distribution/app.jar");
    createFile("build/reports/checkstyle.html");
    createFile("build/reports/test.html");
    createFile("build/java/doc/index.html");
    createFile("build/python/doc/readme.txt");
    createFile("build/java/reports/doc/deep.txt");
    createFile("src/Main.java");
    createFile("src/Test.java");
  }

  private void createFile(String relativePath) throws IOException {
    Path file = workspace.resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.writeString(file, "content");
  }

  @Test
  void exactFile() {
    List<String> result = collector.collect(
        List.of("README.md"), workspace);
    assertEquals(List.of("README.md"), result);
  }

  @Test
  void exactFileNested() {
    List<String> result = collector.collect(
        List.of("build/distribution/app.jar"), workspace);
    assertEquals(1, result.size());
    assertTrue(result.get(0).endsWith("app.jar"));
  }

  @Test
  void directoryPattern() {
    List<String> result = collector.collect(
        List.of("build/reports/"), workspace);
    assertEquals(2, result.size());
    assertTrue(result.stream().anyMatch(
        p -> p.contains("checkstyle.html")));
    assertTrue(result.stream().anyMatch(
        p -> p.contains("test.html")));
  }

  @Test
  void directoryPatternRecursive() {
    List<String> result = collector.collect(
        List.of("build/"), workspace);
    // Should include all files under build/
    assertTrue(result.size() >= 5);
  }

  @Test
  void singleWildcardExtension() {
    List<String> result = collector.collect(
        List.of("src/*.java"), workspace);
    assertEquals(2, result.size());
    assertTrue(result.stream().allMatch(p -> p.endsWith(".java")));
  }

  @Test
  void singleWildcardInPath() {
    // build/*/doc/ should match build/java/doc and build/python/doc
    // but NOT build/java/reports/doc (too deep)
    List<String> result = collector.collect(
        List.of("build/*/doc/"), workspace);
    assertEquals(2, result.size());
    assertTrue(result.stream().anyMatch(
        p -> p.contains("index.html")));
    assertTrue(result.stream().anyMatch(
        p -> p.contains("readme.txt")));
  }

  @Test
  void doubleWildcard() {
    // build/**/doc/ should match doc at any depth
    List<String> result = collector.collect(
        List.of("build/**/doc/"), workspace);
    assertTrue(result.size() >= 3,
        "Expected at least 3 files: " + result);
    assertTrue(result.stream().anyMatch(
        p -> p.contains("deep.txt")));
  }

  @Test
  void wildcardHtmlFiles() {
    List<String> result = collector.collect(
        List.of("build/reports/*.html"), workspace);
    assertEquals(2, result.size());
  }

  @Test
  void doubleWildcardDirectoryNoTrailingSlash() {
    // build/**/distribution should match the distribution directory
    // at any depth and collect all its contents (spec example 4)
    List<String> result = collector.collect(
        List.of("build/**/distribution"), workspace);
    assertEquals(1, result.size());
    assertTrue(result.stream().anyMatch(
        p -> p.contains("app.jar")));
  }

  @Test
  void prefixWildcard() {
    // READ* should match all files starting with READ (spec example)
    List<String> result = collector.collect(
        List.of("READ*"), workspace);
    assertEquals(1, result.size());
    assertTrue(result.get(0).equals("README.md"));
  }

  @Test
  void nonexistentPattern() {
    List<String> result = collector.collect(
        List.of("nonexistent.txt"), workspace);
    assertTrue(result.isEmpty());
  }

  @Test
  void emptyPatterns() {
    List<String> result = collector.collect(
        List.of(), workspace);
    assertTrue(result.isEmpty());
  }

  @Test
  void nullPatterns() {
    List<String> result = collector.collect(null, workspace);
    assertTrue(result.isEmpty());
  }
}

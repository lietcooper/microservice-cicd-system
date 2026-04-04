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

/** Tests for ArtifactStorageService. */
class ArtifactStorageServiceTest {

  @TempDir
  Path storageDir;

  @TempDir
  Path workspace;

  private ArtifactStorageService storageService;

  @BeforeEach
  void setUp() throws IOException {
    storageService = new ArtifactStorageService(
        storageDir.toString());

    // Create test files in workspace
    Files.createDirectories(workspace.resolve("build/libs"));
    Files.writeString(workspace.resolve("build/libs/app.jar"),
        "jar-content");
    Files.writeString(workspace.resolve("README.md"),
        "readme-content");
  }

  @Test
  void storesArtifactsToCorrectPath() {
    List<String> paths = storageService.store(
        List.of("build/libs/app.jar", "README.md"),
        workspace, "default", 1, "build", "compile");

    assertEquals(2, paths.size());

    // Verify files exist at storage location
    for (String path : paths) {
      assertTrue(Files.exists(Path.of(path)),
          "Expected file at: " + path);
    }

    // Verify path convention
    assertTrue(paths.get(0).contains("default"));
    assertTrue(paths.get(0).contains("1"));
    assertTrue(paths.get(0).contains("build"));
    assertTrue(paths.get(0).contains("compile"));
  }

  @Test
  void emptyListReturnsEmpty() {
    List<String> paths = storageService.store(
        List.of(), workspace, "p", 1, "s", "j");
    assertTrue(paths.isEmpty());
  }

  @Test
  void nullListReturnsEmpty() {
    List<String> paths = storageService.store(
        null, workspace, "p", 1, "s", "j");
    assertTrue(paths.isEmpty());
  }

  @Test
  void nonexistentFileSkipped() {
    List<String> paths = storageService.store(
        List.of("nonexistent.txt"),
        workspace, "p", 1, "s", "j");
    assertTrue(paths.isEmpty());
  }

  @Test
  void fileContentPreserved() throws IOException {
    List<String> paths = storageService.store(
        List.of("README.md"),
        workspace, "default", 1, "build", "compile");

    assertEquals(1, paths.size());
    String content = Files.readString(Path.of(paths.get(0)));
    assertEquals("readme-content", content);
  }
}

package cicd.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/** Tests for ArtifactStorageService (MinIO-backed). */
class ArtifactStorageServiceTest {

  @TempDir
  Path workspace;

  private MinioClient mockClient;
  private ArtifactStorageService storageService;

  @BeforeEach
  void setUp() throws Exception {
    mockClient = mock(MinioClient.class);
    storageService = new ArtifactStorageService(
        mockClient, "cicd-artifacts");

    // Create test files in workspace
    Files.createDirectories(workspace.resolve("build/libs"));
    Files.writeString(workspace.resolve("build/libs/app.jar"),
        "jar-content");
    Files.writeString(workspace.resolve("README.md"),
        "readme-content");
  }

  @Test
  void storesArtifactsWithCorrectKeys() throws Exception {
    List<String> keys = storageService.store(
        List.of("build/libs/app.jar", "README.md"),
        workspace, "default", 1, "build", "compile");

    assertEquals(2, keys.size());
    assertEquals("default/1/build/compile/build/libs/app.jar",
        keys.get(0));
    assertEquals("default/1/build/compile/README.md",
        keys.get(1));

    verify(mockClient, times(2))
        .putObject(any(PutObjectArgs.class));
  }

  @Test
  void emptyListReturnsEmpty() {
    List<String> keys = storageService.store(
        List.of(), workspace, "p", 1, "s", "j");
    assertTrue(keys.isEmpty());
  }

  @Test
  void nullListReturnsEmpty() {
    List<String> keys = storageService.store(
        null, workspace, "p", 1, "s", "j");
    assertTrue(keys.isEmpty());
  }

  @Test
  void nonexistentFileSkipped() {
    List<String> keys = storageService.store(
        List.of("nonexistent.txt"),
        workspace, "p", 1, "s", "j");
    assertTrue(keys.isEmpty());
  }

  @Test
  void objectKeyContainsPipelineHierarchy() throws Exception {
    List<String> keys = storageService.store(
        List.of("README.md"),
        workspace, "mypipe", 42, "deploy", "push");

    assertEquals(1, keys.size());
    assertEquals("mypipe/42/deploy/push/README.md", keys.get(0));

    ArgumentCaptor<PutObjectArgs> captor =
        ArgumentCaptor.forClass(PutObjectArgs.class);
    verify(mockClient).putObject(captor.capture());
    assertEquals("cicd-artifacts", captor.getValue().bucket());
    assertEquals("mypipe/42/deploy/push/README.md",
        captor.getValue().object());
  }
}

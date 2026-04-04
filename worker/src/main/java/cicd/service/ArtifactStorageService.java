package cicd.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Copies matched artifact files from the workspace to a persistent
 * storage location so they survive container cleanup.
 *
 * <p>Storage path convention:
 * {@code {storage-root}/{pipeline}/{run-no}/{stage}/{job}/{relative-path}}
 */
@Service
public class ArtifactStorageService {

  private static final Logger log =
      LoggerFactory.getLogger(ArtifactStorageService.class);

  private final Path storageRoot;

  /** Creates the service with configurable storage root. */
  public ArtifactStorageService(
      @Value("${cicd.artifacts.storage-dir:/tmp/cicd-artifacts}")
      String storageDir) {
    this.storageRoot = Path.of(storageDir);
  }

  /**
   * Stores artifact files from workspace to persistent storage.
   *
   * @param relativePaths artifact paths relative to workspace root
   * @param workspaceRoot the workspace directory
   * @param pipeline      pipeline name
   * @param runNo         run number
   * @param stage         stage name
   * @param job           job name
   * @return list of storage paths where artifacts were saved
   */
  public List<String> store(List<String> relativePaths,
      Path workspaceRoot, String pipeline, int runNo,
      String stage, String job) {
    List<String> storagePaths = new ArrayList<>();

    if (relativePaths == null || relativePaths.isEmpty()) {
      return storagePaths;
    }

    Path destBase = storageRoot
        .resolve(pipeline)
        .resolve(String.valueOf(runNo))
        .resolve(stage)
        .resolve(job);

    for (String relativePath : relativePaths) {
      Path source = workspaceRoot.resolve(relativePath);
      Path dest = destBase.resolve(relativePath);

      try {
        Files.createDirectories(dest.getParent());
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        storagePaths.add(dest.toString());
        log.debug("Stored artifact: {} -> {}", relativePath, dest);
      } catch (IOException ex) {
        log.warn("Failed to store artifact '{}': {}",
            relativePath, ex.getMessage());
      }
    }

    log.info("Stored {} artifact(s) for job '{}'",
        storagePaths.size(), job);
    return storagePaths;
  }

  /** Returns the storage root path. */
  public Path getStorageRoot() {
    return storageRoot;
  }
}

package cicd.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

/** Creates zip archives of workspace directories. */
@Service
public class WorkspaceArchiveService {

  /** Archives the given workspace directory as a zip byte array. */
  public byte[] createArchive(Path workspaceDir) {
    try {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      try (ZipOutputStream zipOut = new ZipOutputStream(buffer)) {
        Files.walk(workspaceDir)
            .filter(path -> !Files.isDirectory(path))
            .forEach(path -> addEntry(workspaceDir, path, zipOut));
      }
      return buffer.toByteArray();
    } catch (IOException ex) {
      throw new RuntimeException(
          "Failed to create workspace archive", ex);
    }
  }

  private void addEntry(Path workspaceDir, Path file,
      ZipOutputStream zipOut) {
    Path relativePath = workspaceDir.relativize(file);
    ZipEntry entry = new ZipEntry(relativePath.toString());
    try {
      zipOut.putNextEntry(entry);
      try (InputStream input = Files.newInputStream(file)) {
        input.transferTo(zipOut);
      }
      zipOut.closeEntry();
    } catch (IOException ex) {
      throw new RuntimeException(
          "Failed to add file to workspace archive: "
          + relativePath, ex);
    }
  }
}

package cicd.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceArchiveService {

  public Path extractArchive(byte[] archiveBytes) {
    if (archiveBytes == null || archiveBytes.length == 0) {
      throw new IllegalArgumentException("workspace archive is empty");
    }
    try {
      Path workspaceDir = Files.createTempDirectory("cicd-workspace-");
      try (ZipInputStream zipIn = new ZipInputStream(
          new java.io.ByteArrayInputStream(archiveBytes))) {
        ZipEntry entry;
        while ((entry = zipIn.getNextEntry()) != null) {
          Path target = workspaceDir.resolve(entry.getName()).normalize();
          if (!target.startsWith(workspaceDir)) {
            throw new IllegalArgumentException("invalid archive entry: "
                + entry.getName());
          }
          if (entry.isDirectory()) {
            Files.createDirectories(target);
          } else {
            Files.createDirectories(target.getParent());
            try (OutputStream output = Files.newOutputStream(target)) {
              copy(zipIn, output);
            }
          }
          zipIn.closeEntry();
        }
      }
      return workspaceDir;
    } catch (IOException e) {
      throw new RuntimeException("Failed to extract workspace archive", e);
    }
  }

  public void cleanupWorkspace(Path workspaceDir) {
    if (workspaceDir == null) {
      return;
    }
    try (var walk = Files.walk(workspaceDir)) {
      walk.sorted(Comparator.reverseOrder())
          .forEach(path -> path.toFile().delete());
    } catch (IOException e) {
      System.err.println("Failed to cleanup workspace directory: "
          + e.getMessage());
    }
  }

  private void copy(InputStream input, OutputStream output) throws IOException {
    byte[] buffer = new byte[8192];
    int read;
    while ((read = input.read(buffer)) != -1) {
      output.write(buffer, 0, read);
    }
  }
}

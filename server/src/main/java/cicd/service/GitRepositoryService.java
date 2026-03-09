package cicd.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class GitRepositoryService {

  public Path createSnapshot(String repoUrl, String branch, String commit) {
    try {
      Path tempDir = Files.createTempDirectory("cicd-snapshot-");
      runGit(tempDir.toFile(), "clone", repoUrl, ".");

      if (branch != null && !branch.isBlank()) {
        ensureBranchExists(tempDir, branch);
        runGit(tempDir.toFile(), "checkout", branch);
      }

      if (commit != null && !commit.isBlank()) {
        ensureCommitExists(tempDir, commit);
        if (branch != null && !branch.isBlank()) {
          ensureCommitBelongsToBranch(tempDir, branch, commit);
        }
        runGit(tempDir.toFile(), "checkout", commit);
      }

      return tempDir;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to create repository snapshot: " + e.getMessage(), e);
    }
  }

  public void cleanupSnapshot(Path snapshotPath) {
    if (snapshotPath == null) {
      return;
    }
    try (Stream<Path> walk = Files.walk(snapshotPath)) {
      walk.sorted(Comparator.reverseOrder())
          .map(Path::toFile)
          .forEach(File::delete);
    } catch (Exception e) {
      System.err.println("Failed to cleanup snapshot directory: "
          + e.getMessage());
    }
  }

  public String getActualCommitHash(Path snapshotPath) {
    return runGit(snapshotPath.toFile(), "rev-parse", "HEAD").trim();
  }

  public String getActualBranchName(Path snapshotPath) {
    return runGit(snapshotPath.toFile(), "rev-parse", "--abbrev-ref", "HEAD")
        .trim();
  }

  private void ensureBranchExists(Path snapshotPath, String branch) {
    try {
      runGit(snapshotPath.toFile(), "rev-parse", "--verify",
          "refs/remotes/origin/" + branch);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("branch not found: " + branch, e);
    }
  }

  private void ensureCommitExists(Path snapshotPath, String commit) {
    try {
      runGit(snapshotPath.toFile(), "rev-parse", "--verify", commit + "^{commit}");
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("commit not found: " + commit, e);
    }
  }

  private void ensureCommitBelongsToBranch(Path snapshotPath,
      String branch, String commit) {
    String branches = runGit(snapshotPath.toFile(), "branch", "-r",
        "--contains", commit);
    if (!branches.contains("origin/" + branch)) {
      throw new IllegalArgumentException(
          "commit " + commit + " does not belong to branch " + branch);
    }
  }

  private String runGit(File directory, String... args) {
    try {
      String[] cmd = new String[args.length + 1];
      cmd[0] = "git";
      System.arraycopy(args, 0, cmd, 1, args.length);

      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.directory(directory);
      Process p = pb.start();

      BufferedReader reader = new BufferedReader(
          new InputStreamReader(p.getInputStream()));
      StringBuilder output = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        output.append(line).append("\n");
      }

      int exitCode = p.waitFor();
      if (exitCode != 0) {
        BufferedReader errReader = new BufferedReader(
            new InputStreamReader(p.getErrorStream()));
        StringBuilder error = new StringBuilder();
        while ((line = errReader.readLine()) != null) {
          error.append(line).append("\n");
        }
        throw new RuntimeException("Git command failed with exit code "
            + exitCode + ": " + error.toString().trim());
      }
      return output.toString();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to execute git command", e);
    }
  }
}

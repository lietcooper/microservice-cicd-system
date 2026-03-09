package cicd.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/** Manages git repository snapshots for pipeline execution. */
@Service
public class GitRepositoryService {

  /** Clones the repo and checks out the given branch/commit. */
  public Path createSnapshot(String repoUrl, String branch,
      String commit) {
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
    } catch (RuntimeException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new RuntimeException(
          "Failed to create repository snapshot: "
          + ex.getMessage(), ex);
    }
  }

  /** Deletes the snapshot directory recursively. */
  public void cleanupSnapshot(Path snapshotPath) {
    if (snapshotPath == null) {
      return;
    }
    try (Stream<Path> walk = Files.walk(snapshotPath)) {
      walk.sorted(Comparator.reverseOrder())
          .map(Path::toFile)
          .forEach(File::delete);
    } catch (Exception ex) {
      System.err.println(
          "Failed to cleanup snapshot directory: "
          + ex.getMessage());
    }
  }

  /** Returns the HEAD commit hash for the snapshot. */
  public String getActualCommitHash(Path snapshotPath) {
    return runGit(snapshotPath.toFile(),
        "rev-parse", "HEAD").trim();
  }

  /** Returns the current branch name for the snapshot. */
  public String getActualBranchName(Path snapshotPath) {
    return runGit(snapshotPath.toFile(),
        "rev-parse", "--abbrev-ref", "HEAD").trim();
  }

  private void ensureBranchExists(Path snapshotPath, String branch) {
    try {
      runGit(snapshotPath.toFile(), "rev-parse", "--verify",
          "refs/remotes/origin/" + branch);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException(
          "branch not found: " + branch, ex);
    }
  }

  private void ensureCommitExists(Path snapshotPath, String commit) {
    try {
      runGit(snapshotPath.toFile(), "rev-parse", "--verify",
          commit + "^{commit}");
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException(
          "commit not found: " + commit, ex);
    }
  }

  private void ensureCommitBelongsToBranch(Path snapshotPath,
      String branch, String commit) {
    String branches = runGit(snapshotPath.toFile(), "branch", "-r",
        "--contains", commit);
    if (!branches.contains("origin/" + branch)) {
      throw new IllegalArgumentException(
          "commit " + commit
          + " does not belong to branch " + branch);
    }
  }

  private String runGit(File directory, String... args) {
    try {
      String[] cmd = new String[args.length + 1];
      cmd[0] = "git";
      System.arraycopy(args, 0, cmd, 1, args.length);

      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.directory(directory);
      Process proc = pb.start();

      BufferedReader reader = new BufferedReader(
          new InputStreamReader(proc.getInputStream()));
      StringBuilder output = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        output.append(line).append("\n");
      }

      int exitCode = proc.waitFor();
      if (exitCode != 0) {
        BufferedReader errReader = new BufferedReader(
            new InputStreamReader(proc.getErrorStream()));
        StringBuilder error = new StringBuilder();
        while ((line = errReader.readLine()) != null) {
          error.append(line).append("\n");
        }
        throw new RuntimeException(
            "Git command failed with exit code "
            + exitCode + ": " + error.toString().trim());
      }
      return output.toString();
    } catch (RuntimeException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new RuntimeException(
          "Failed to execute git command", ex);
    }
  }
}

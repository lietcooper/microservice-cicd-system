package cicd.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/** Utility class for executing Git commands. */
public class GitHelper {

  /** Returns the current branch name. */
  public static String currentBranch(String repoPath) {
    return git(repoPath, "rev-parse", "--abbrev-ref", "HEAD");
  }

  /** Returns the abbreviated current commit hash. */
  public static String currentCommit(String repoPath) {
    return git(repoPath, "rev-parse", "--short", "HEAD");
  }

  /** Returns the full current commit hash. */
  public static String currentCommitFull(String repoPath) {
    return git(repoPath, "rev-parse", "HEAD");
  }

  /** Returns the remote origin URL. */
  public static String remoteOriginUrl(String repoPath) {
    return git(repoPath, "remote", "get-url", "origin");
  }

  /** Returns true if the path contains a .git directory. */
  public static boolean isGitRoot(String path) {
    return new File(path, ".git").isDirectory();
  }

  private static String git(String repoPath, String... args) {
    try {
      String[] cmd = new String[args.length + 1];
      cmd[0] = "git";
      System.arraycopy(args, 0, cmd, 1, args.length);

      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.directory(new File(repoPath));
      Process proc = pb.start();

      BufferedReader reader = new BufferedReader(
          new InputStreamReader(proc.getInputStream()));
      BufferedReader errReader = new BufferedReader(
          new InputStreamReader(proc.getErrorStream()));

      String line = reader.readLine();
      int exitCode = proc.waitFor();

      if (exitCode != 0) {
        StringBuilder sb = new StringBuilder();
        String errLine;
        while ((errLine = errReader.readLine()) != null) {
          sb.append(errLine).append("\n");
        }
        throw new RuntimeException(
            "Git command failed with exit code "
                + exitCode + ": " + sb.toString().trim());
      }
      return line != null ? line.trim() : "";
    } catch (RuntimeException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new RuntimeException(
          "Failed to execute git command", ex);
    }
  }
}

package cicd.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class GitHelper {

  public static String currentBranch(String repoPath) {
    return git(repoPath, "rev-parse", "--abbrev-ref", "HEAD");
  }

  public static String currentCommit(String repoPath) {
    return git(repoPath, "rev-parse", "--short", "HEAD");
  }

  public static String currentCommitFull(String repoPath) {
    return git(repoPath, "rev-parse", "HEAD");
  }

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
      Process p = pb.start();

      BufferedReader reader = new BufferedReader(
          new InputStreamReader(p.getInputStream()));
      BufferedReader errReader = new BufferedReader(
          new InputStreamReader(p.getErrorStream()));
      
      String line = reader.readLine();
      int exitCode = p.waitFor();

      if (exitCode != 0) {
        StringBuilder sb = new StringBuilder();
        String errLine;
        while ((errLine = errReader.readLine()) != null) {
          sb.append(errLine).append("\n");
        }
        throw new RuntimeException("Git command failed with exit code " + exitCode + ": " + sb.toString().trim());
      }
      return line != null ? line.trim() : "";
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to execute git command", e);
    }
  }
}

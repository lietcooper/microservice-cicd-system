package cicd.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GitHelperTest {

  @Test
  void testIsGitRootTrue() {
    assertTrue(GitHelper.isGitRoot(
        System.getProperty("user.dir")));
  }

  @Test
  void testIsGitRootFalse() {
    assertFalse(GitHelper.isGitRoot("/tmp"));
  }

  @Test
  void testIsGitRootForNonExistentPath() {
    assertFalse(GitHelper.isGitRoot("/no/such/path/at/all"));
  }

  @Test
  void testCurrentBranchInGitRepo() {
    String branch = GitHelper.currentBranch(
        System.getProperty("user.dir"));
    assertFalse(branch.isEmpty());
  }

  @Test
  void testCurrentBranchThrowsForNonRepo() {
    assertThrows(RuntimeException.class,
        () -> GitHelper.currentBranch("/tmp"));
  }

  @Test
  void testCurrentBranchThrowsForNonExistentPath() {
    assertThrows(RuntimeException.class,
        () -> GitHelper.currentBranch("/no/such/path"));
  }

  @Test
  void testCurrentCommit() {
    String commit = GitHelper.currentCommit(
        System.getProperty("user.dir"));
    assertFalse(commit.isEmpty());
  }

  @Test
  void testCurrentCommitIsShort() {
    String commit = GitHelper.currentCommit(
        System.getProperty("user.dir"));
    assertTrue(commit.length() >= 7 && commit.length() <= 12,
        "Short commit hash should be 7-12 chars, got: "
            + commit.length());
  }

  @Test
  void testCurrentCommitThrowsForNonRepo() {
    assertThrows(RuntimeException.class,
        () -> GitHelper.currentCommit("/tmp"));
  }

  @Test
  void testCurrentCommitFull() {
    String fullCommit = GitHelper.currentCommitFull(
        System.getProperty("user.dir"));
    assertFalse(fullCommit.isEmpty());
  }

  @Test
  void testCurrentCommitFullIsLong() {
    String fullCommit = GitHelper.currentCommitFull(
        System.getProperty("user.dir"));
    assertEquals(40, fullCommit.length(),
        "Full commit hash should be exactly 40 chars");
  }

  @Test
  void testCurrentCommitFullStartsWithShortCommit() {
    String repoPath = System.getProperty("user.dir");
    String shortCommit = GitHelper.currentCommit(repoPath);
    String fullCommit = GitHelper.currentCommitFull(repoPath);

    assertFalse(shortCommit.isEmpty());
    assertFalse(fullCommit.isEmpty());
    assertTrue(fullCommit.startsWith(shortCommit),
        "Full commit should start with short commit hash");
  }

  @Test
  void testCurrentCommitFullThrowsForNonRepo() {
    assertThrows(RuntimeException.class,
        () -> GitHelper.currentCommitFull("/tmp"));
  }

  @Test
  void testBranchAndCommitAreConsistent() {
    String repoPath = System.getProperty("user.dir");
    String branch = GitHelper.currentBranch(repoPath);
    String commit = GitHelper.currentCommit(repoPath);

    assertFalse(branch.isEmpty());
    assertFalse(commit.isEmpty());
  }
}

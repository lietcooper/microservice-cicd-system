package cicd.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GitHelperTest {

  @Test
  void testIsGitRootTrue() {
    assertTrue(GitHelper.isGitRoot(System.getProperty("user.dir")));
  }

  @Test
  void testIsGitRootFalse() {
    assertFalse(GitHelper.isGitRoot("/tmp"));
  }

  @Test
  void testCurrentBranch() {
    String branch = GitHelper.currentBranch(System.getProperty("user.dir"));
    assertFalse(branch.isEmpty());
  }

  @Test
  void testCurrentCommit() {
    String commit = GitHelper.currentCommit(System.getProperty("user.dir"));
    assertFalse(commit.isEmpty());
  }

  @Test
  void testNonRepoReturnsEmpty() {
    assertEquals("", GitHelper.currentBranch("/tmp"));
  }
}

package cicd.cmd;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import static org.junit.jupiter.api.Assertions.*;

class RunCmdTest {

  @Test
  void testNoArgs() {
    int code = new CommandLine(new RunCmd()).execute();
    assertEquals(1, code);
  }

  @Test
  void testBothNameAndFile() {
    int code = new CommandLine(new RunCmd()).execute(
        "--name", "default", "--file", "some.yaml");
    assertEquals(1, code);
  }

  @Test
  void testFileNotFound() {
    int code = new CommandLine(new RunCmd()).execute(
        "--file", "nonexistent.yaml");
    assertEquals(1, code);
  }

  @Test
  void testBranchMismatch() {
    int code = new CommandLine(new RunCmd()).execute(
        "--file", ".pipelines/default.yaml",
        "--branch", "fake-branch-xyz");
    assertEquals(1, code);
  }

  @Test
  void testCommitMismatch() {
    int code = new CommandLine(new RunCmd()).execute(
        "--file", ".pipelines/default.yaml",
        "--commit", "0000000");
    assertEquals(1, code);
  }

  @Test
  void testValidFile() {
    // 应该走到 "Ready to execute" 那步，返回 0
    int code = new CommandLine(new RunCmd()).execute(
        "--file", ".pipelines/default.yaml");
    assertEquals(0, code);
  }

  @Test
  void testValidName() {
    int code = new CommandLine(new RunCmd()).execute(
        "--name", "default");
    assertEquals(0, code);
  }
}
package cicd.cmd;

import cicd.docker.MockDockerRunner;
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
    RunCmd cmd = new RunCmd();
    cmd.setDockerRunner(new MockDockerRunner());
    int code = new CommandLine(cmd).execute(
        "--file", ".pipelines/default.yaml");
    assertEquals(0, code);
  }

  @Test
  void testValidName() {
    RunCmd cmd = new RunCmd();
    cmd.setDockerRunner(new MockDockerRunner());
    int code = new CommandLine(cmd).execute(
        "--name", "default");
    assertEquals(0, code);
  }
}

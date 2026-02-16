package cicd.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cicd.executor.JobResult;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.KillContainerCmd;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.WaitContainerCmd;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalDockerRunnerTest {

  private DockerClient dockerClient;
  private LocalDockerRunner runner;

  private PullImageCmd pullCmd;
  private PullImageResultCallback pullCallback;
  private CreateContainerCmd createCmd;
  private StartContainerCmd startCmd;
  private WaitContainerCmd waitCmd;
  private WaitContainerResultCallback waitCallback;
  private LogContainerCmd logCmd;
  private RemoveContainerCmd removeCmd;

  @BeforeEach
  void setUp() throws InterruptedException {
    dockerClient = mock(DockerClient.class);
    runner = new LocalDockerRunner(dockerClient);

    pullCmd = mock(PullImageCmd.class);
    pullCallback = mock(PullImageResultCallback.class);
    createCmd = mock(CreateContainerCmd.class);
    startCmd = mock(StartContainerCmd.class);
    waitCmd = mock(WaitContainerCmd.class);
    waitCallback = mock(WaitContainerResultCallback.class);
    logCmd = mock(LogContainerCmd.class);
    removeCmd = mock(RemoveContainerCmd.class);

    // Default wiring
    when(dockerClient.pullImageCmd(anyString())).thenReturn(pullCmd);
    when(pullCmd.start()).thenReturn(pullCallback);
    when(pullCallback.awaitCompletion(any(Long.class),
        any(TimeUnit.class))).thenReturn(true);

    when(dockerClient.createContainerCmd(anyString()))
        .thenReturn(createCmd);
    when(createCmd.withHostConfig(any(HostConfig.class)))
        .thenReturn(createCmd);
    when(createCmd.withWorkingDir(anyString())).thenReturn(createCmd);
    when(createCmd.withCmd(any(String[].class))).thenReturn(createCmd);
    CreateContainerResponse response = new CreateContainerResponse();
    response.setId("container-123");
    when(createCmd.exec()).thenReturn(response);

    when(dockerClient.startContainerCmd(anyString()))
        .thenReturn(startCmd);

    when(dockerClient.waitContainerCmd(anyString()))
        .thenReturn(waitCmd);
    when(waitCmd.start()).thenReturn(waitCallback);
    when(waitCallback.awaitCompletion(any(Long.class),
        any(TimeUnit.class))).thenReturn(true);
    when(waitCallback.awaitStatusCode()).thenReturn(0);

    when(dockerClient.logContainerCmd(anyString()))
        .thenReturn(logCmd);
    when(logCmd.withStdOut(true)).thenReturn(logCmd);
    when(logCmd.withStdErr(true)).thenReturn(logCmd);
    when(logCmd.withFollowStream(false)).thenReturn(logCmd);
    doAnswer(inv -> {
      ResultCallback<Frame> cb = inv.getArgument(0);
      cb.onComplete();
      return cb;
    }).when(logCmd).exec(any());

    when(dockerClient.removeContainerCmd(anyString()))
        .thenReturn(removeCmd);
    when(removeCmd.withForce(true)).thenReturn(removeCmd);
  }

  @Test
  void successfulJobReturnsZeroExitCode() {
    JobResult result = runner.runJob("alpine:latest",
        List.of("echo hello"), "/tmp/repo");

    assertTrue(result.ok());
    assertEquals(0, result.exitCode);
    assertEquals("alpine:latest", result.jobName);
  }

  @Test
  void failedScriptReturnsNonZeroExitCode() {
    when(waitCallback.awaitStatusCode()).thenReturn(1);

    JobResult result = runner.runJob("alpine:latest",
        List.of("exit 1"), "/tmp/repo");

    assertFalse(result.ok());
    assertEquals(1, result.exitCode);
  }

  @Test
  void imageNotFoundReturnsError() {
    when(dockerClient.pullImageCmd(anyString()))
        .thenThrow(new NotFoundException("no such image"));

    JobResult result = runner.runJob("nonexistent:latest",
        List.of("echo hi"), "/tmp/repo");

    assertFalse(result.ok());
    assertTrue(result.output.contains("image not found"));
  }

  @Test
  void containerIsAlwaysRemoved() {
    runner.runJob("alpine:latest", List.of("echo hi"), "/tmp/repo");

    verify(dockerClient).removeContainerCmd("container-123");
  }

  @Test
  void containerRemovedEvenOnFailure() {
    when(dockerClient.startContainerCmd(anyString()))
        .thenThrow(new RuntimeException("start failed"));

    runner.runJob("alpine:latest", List.of("echo hi"), "/tmp/repo");

    verify(dockerClient).removeContainerCmd("container-123");
  }

  @Test
  void timeoutKillsContainerAndReturnsError()
      throws InterruptedException {
    when(waitCallback.awaitCompletion(any(Long.class),
        any(TimeUnit.class))).thenReturn(false);
    KillContainerCmd killCmd = mock(KillContainerCmd.class);
    when(dockerClient.killContainerCmd(anyString()))
        .thenReturn(killCmd);

    JobResult result = runner.runJob("alpine:latest",
        List.of("sleep 9999"), "/tmp/repo");

    assertFalse(result.ok());
    assertTrue(result.output.contains("timed out"));
    verify(dockerClient).killContainerCmd("container-123");
  }

  @Test
  void dockerDaemonErrorReturnsDescriptiveMessage() {
    when(dockerClient.pullImageCmd(anyString()))
        .thenThrow(new RuntimeException("Cannot connect to daemon"));

    JobResult result = runner.runJob("alpine:latest",
        List.of("echo hi"), "/tmp/repo");

    assertFalse(result.ok());
    assertTrue(result.output.contains("docker error"));
    assertTrue(result.output.contains("Cannot connect to daemon"));
  }

  @Test
  void cleanupFailureDoesNotThrow() {
    doThrow(new RuntimeException("remove failed"))
        .when(removeCmd).exec();

    JobResult result = runner.runJob("alpine:latest",
        List.of("echo hi"), "/tmp/repo");

    // Should still return a result without throwing
    assertEquals("alpine:latest", result.jobName);
  }

  @Test
  void multipleScriptsAreJoinedWithAnd() {
    runner.runJob("alpine:latest",
        List.of("echo a", "echo b", "echo c"), "/tmp/repo");

    verify(createCmd).withCmd("sh", "-c",
        "echo a && echo b && echo c");
  }
}

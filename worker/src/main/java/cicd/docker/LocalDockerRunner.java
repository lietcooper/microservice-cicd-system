package cicd.docker;

import cicd.executor.JobResult;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Runs pipeline jobs inside local Docker containers.
 */
public class LocalDockerRunner implements DockerRunner {

  private static final Logger log =
      LoggerFactory.getLogger(LocalDockerRunner.class);

  private static final long PULL_TIMEOUT_SECS = 300;
  private static final long EXEC_TIMEOUT_SECS = 600;
  private static final long LOG_TIMEOUT_SECS = 30;
  private static final String WORKSPACE_DIR = "/workspace";

  private final DockerClient dockerClient;

  /** Creates a runner connected to the local Docker daemon. */
  public LocalDockerRunner() {
    DefaultDockerClientConfig config =
        DefaultDockerClientConfig.createDefaultConfigBuilder().build();
    DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
        .dockerHost(config.getDockerHost())
        .build();
    this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
  }

  /** Creates a runner with a provided DockerClient (for testing). */
  LocalDockerRunner(DockerClient dockerClient) {
    this.dockerClient = dockerClient;
  }

  /**
   * Runs a job inside a Docker container.
   *
   * @param image    the Docker image to use
   * @param scripts  shell commands to execute sequentially
   * @param repoPath absolute path to the repository to mount
   * @return result containing exit code and captured output
   */
  @Override
  public JobResult runJob(String image, List<String> scripts,
      String repoPath) {
    String containerId = null;
    try {
      pullImage(image);
      String shellCommand = String.join(" && ", scripts);
      containerId = createContainer(image, shellCommand, repoPath);
      dockerClient.startContainerCmd(containerId).exec();

      // Stream container logs in real-time via SLF4J
      StringBuilder output = new StringBuilder();
      ResultCallback.Adapter<Frame> logCallback =
          startLogStreaming(containerId, output);

      int exitCode = awaitCompletion(containerId);

      // Wait for log stream to finish after container exits
      logCallback.awaitCompletion(LOG_TIMEOUT_SECS, TimeUnit.SECONDS);

      if (exitCode < 0) {
        return new JobResult(image, 1,
            "job timed out after " + EXEC_TIMEOUT_SECS + " seconds");
      }

      // Copy workspace back from container to host.
      // In DinD, bind-mount writes land on the DinD overlay,
      // not on the shared emptyDir, so we must docker-cp.
      copyWorkspaceFromContainer(containerId, repoPath);

      return new JobResult(image, exitCode, output.toString());
    } catch (NotFoundException ex) {
      return new JobResult(image, 1, "image not found: " + image);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return new JobResult(image, 1, "execution interrupted");
    } catch (Exception ex) {
      return new JobResult(image, 1, "docker error: "
          + (ex.getMessage() != null ? ex.getMessage()
              : ex.getClass().getName()));
    } finally {
      cleanupContainer(containerId);
    }
  }

  private void pullImage(String image) throws InterruptedException {
    boolean done = dockerClient.pullImageCmd(image)
        .start()
        .awaitCompletion(PULL_TIMEOUT_SECS, TimeUnit.SECONDS);
    if (!done) {
      throw new RuntimeException(
          "image pull timed out after " + PULL_TIMEOUT_SECS + " seconds");
    }
  }

  private String createContainer(String image, String command,
      String repoPath) {
    HostConfig hostConfig = HostConfig.newHostConfig()
        .withBinds(new Bind(repoPath, new Volume(WORKSPACE_DIR)));

    CreateContainerResponse response = dockerClient
        .createContainerCmd(image)
        .withHostConfig(hostConfig)
        .withWorkingDir(WORKSPACE_DIR)
        .withCmd("sh", "-c", command)
        .exec();
    return response.getId();
  }

  private int awaitCompletion(String containerId)
      throws InterruptedException {
    WaitContainerResultCallback callback = dockerClient
        .waitContainerCmd(containerId)
        .start();
    boolean done = callback.awaitCompletion(
        EXEC_TIMEOUT_SECS, TimeUnit.SECONDS);
    if (!done) {
      killContainer(containerId);
      return -1;
    }
    return callback.awaitStatusCode();
  }

  /**
   * Starts streaming container logs in real-time via SLF4J.
   * Each log line is emitted with MDC source=job-container.
   * Output is also captured into the StringBuilder for JobResult.
   *
   * @param containerId the Docker container to stream logs from
   * @param output      buffer to accumulate full output
   * @return the callback adapter (caller must awaitCompletion)
   */
  private ResultCallback.Adapter<Frame> startLogStreaming(
      String containerId, StringBuilder output) {
    Map<String, String> callerMdc = MDC.getCopyOfContextMap();
    ResultCallback.Adapter<Frame> callback =
        new ResultCallback.Adapter<>() {
          @Override
          public void onNext(Frame frame) {
            String text = new String(frame.getPayload(),
                StandardCharsets.UTF_8);
            output.append(text);

            // Restore caller MDC context on the I/O thread
            if (callerMdc != null) {
              MDC.setContextMap(callerMdc);
            }
            MDC.put("source", "job-container");
            for (String line : text.stripTrailing().split("\n")) {
              if (!line.isEmpty()) {
                log.info("{}", line);
              }
            }
            MDC.clear();
          }
        };
    dockerClient.logContainerCmd(containerId)
        .withStdOut(true)
        .withStdErr(true)
        .withFollowStream(true)
        .exec(callback);
    return callback;
  }

  /**
   * Copies the workspace directory from a stopped container back to the
   * host path. Needed in DinD where bind-mount writes land on the DinD
   * overlay filesystem instead of the shared emptyDir volume.
   */
  private void copyWorkspaceFromContainer(String containerId,
      String hostPath) {
    try (java.io.InputStream tar = dockerClient
        .copyArchiveFromContainerCmd(containerId, WORKSPACE_DIR + "/.")
        .exec();
        org.apache.commons.compress.archivers.tar.TarArchiveInputStream tis =
            new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(
                tar)) {
      org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
      java.nio.file.Path dest = java.nio.file.Path.of(hostPath);
      while ((entry = tis.getNextTarEntry()) != null) {
        java.nio.file.Path target = dest.resolve(entry.getName());
        if (entry.isDirectory()) {
          java.nio.file.Files.createDirectories(target);
        } else {
          java.nio.file.Files.createDirectories(target.getParent());
          java.nio.file.Files.copy(tis, target,
              java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
      }
      log.debug("Copied workspace from container to {}", hostPath);
    } catch (Exception ex) {
      log.warn("Failed to copy workspace from container: {}",
          ex.getMessage());
    }
  }

  private void killContainer(String containerId) {
    try {
      dockerClient.killContainerCmd(containerId).exec();
    } catch (Exception expected) {
      // container may already be stopped
    }
  }

  private void cleanupContainer(String containerId) {
    if (containerId == null) {
      return;
    }
    try {
      dockerClient.removeContainerCmd(containerId)
          .withForce(true)
          .exec();
    } catch (Exception expected) {
      // best-effort cleanup
    }
  }
}

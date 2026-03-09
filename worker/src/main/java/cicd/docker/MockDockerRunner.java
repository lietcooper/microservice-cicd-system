package cicd.docker;

import cicd.executor.JobResult;
import java.util.List;

/** Mock Docker runner for testing without a real Docker daemon. */
public class MockDockerRunner implements DockerRunner {
  @Override
  public JobResult runJob(String image,
      List<String> scripts, String repoPath) {
    return new JobResult("mock", 0, "ok");
  }
}
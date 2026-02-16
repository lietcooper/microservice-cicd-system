package cicd.docker;

import cicd.executor.JobResult;
import java.util.List;

public class MockDockerRunner implements DockerRunner {
  public JobResult runJob(String image, List<String> scripts, String repoPath) {
    return new JobResult("mock", 0, "ok");
  }
}
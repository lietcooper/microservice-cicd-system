package cicd.docker;

import cicd.executor.JobResult;
import java.util.List;

/** Interface for executing jobs in Docker containers. */
public interface DockerRunner {

  /** Runs a job with the given image, scripts, and repository path. */
  JobResult runJob(String image, List<String> scripts, String repoPath);
}

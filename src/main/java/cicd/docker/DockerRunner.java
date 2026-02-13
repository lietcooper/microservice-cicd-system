package cicd.docker;

import cicd.executor.JobResult;
import java.util.List;

public interface DockerRunner {
  JobResult runJob(String image, List<String> scripts, String repoPath);
}
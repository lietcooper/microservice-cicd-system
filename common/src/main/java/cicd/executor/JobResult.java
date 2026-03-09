package cicd.executor;

/** Result of a job execution containing exit code and output. */
public record JobResult(String jobName, int exitCode, String output) {

  /** Returns true if the job succeeded (exit code 0). */
  public boolean ok() {
    return exitCode == 0;
  }
}

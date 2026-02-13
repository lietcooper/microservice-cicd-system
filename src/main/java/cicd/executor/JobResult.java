package cicd.executor;

public class JobResult {
  public final String jobName;
  public final int exitCode;
  public final String output;

  public JobResult(String jobName, int exitCode, String output) {
    this.jobName = jobName;
    this.exitCode = exitCode;
    this.output = output;
  }

  public boolean ok() {
    return exitCode == 0;
  }
}
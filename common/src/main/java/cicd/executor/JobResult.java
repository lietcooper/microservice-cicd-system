package cicd.executor;

public record JobResult(String jobName, int exitCode, String output) {

  public boolean ok() {
    return exitCode == 0;
  }
}
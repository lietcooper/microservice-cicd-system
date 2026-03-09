package cicd.persistence.entity;

/** Pipeline/stage/job execution status. */
public enum RunStatus {
  PENDING,
  RUNNING,
  SUCCESS,
  FAILED;

  /** Returns the status name in lower case. */
  public String getLabel() {
    return name().toLowerCase();
  }
}

package cicd.persistence.entity;

public enum RunStatus {
  PENDING,
  RUNNING,
  SUCCESS,
  FAILED;

  public String getLabel() {
    return name().toLowerCase();
  }
}

package cicd.persistence.entity;

public enum RunStatus {
  RUNNING,
  SUCCESS,
  FAILED;

  public String getLabel() {
    return name().toLowerCase();
  }
}

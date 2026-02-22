package cicd.persistence.entity;

public enum RunStatus {
  SUCCESS,
  FAILED;

  public String getLabel() {
    return name().toLowerCase();
  }
}
